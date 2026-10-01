package com.resumegen.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.config.AiProperties;
import com.resumegen.dto.AiTaskSubmitVO;
import com.resumegen.dto.AiTaskVO;
import com.resumegen.dto.InterviewGenerateRequest;
import com.resumegen.dto.ResumeAiRequest;
import com.resumegen.dto.ResumeScoreRequest;
import com.resumegen.mq.MessageQueue;
import com.resumegen.service.InterviewService;
import com.resumegen.service.ResumeAiService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * AI 异步任务服务：按任务成本分「高 / 低」双队列提交，消费者并发度差异化，削峰解耦。
 *
 * <p>任务分级（可配置并发度）：高成本 {@code interview.generate} / {@code resume.suggest}
 * 走 {@value #QUEUE_HIGH}（并发高）；低成本 {@code resume.rewrite} / {@code resume.expand}
 * 走 {@value #QUEUE_LOW}（并发低）。结果写回内存登记表，经 {@code GET /api/ai/task/{taskId}} 轮询。
 */
@Service
public class AiAsyncService {

    public static final String QUEUE_HIGH = "high";
    public static final String QUEUE_LOW = "low";

    public static final String TASK_INTERVIEW = "interview.generate";
    public static final String TASK_REWRITE = "resume.rewrite";
    public static final String TASK_EXPAND = "resume.expand";
    public static final String TASK_SUGGEST = "resume.suggest";
    public static final String TASK_IMPROVE = "resume.improve";
    public static final String TASK_SCORE = "resume.score";

    private final MessageQueue queue;
    private final InterviewService interviewService;
    private final ResumeAiService resumeAiService;
    private final ObjectMapper om;
    private final AiProperties props;
    private final AiTaskStore taskStore;

    /** 任务完成监听器（SSE 推送用）：taskId → 完成回调集合。 */
    private final Map<String, List<Consumer<AiTaskVO>>> listeners = new ConcurrentHashMap<>();

    public AiAsyncService(MessageQueue queue, InterviewService interviewService,
                          ResumeAiService resumeAiService, ObjectMapper om, AiProperties props,
                          AiTaskStore taskStore) {
        this.queue = queue;
        this.interviewService = interviewService;
        this.resumeAiService = resumeAiService;
        this.om = om;
        this.props = props;
        this.taskStore = taskStore;
    }

    @PostConstruct
    void start() {
        if (!props.getAsync().isEnabled()) {
            return;
        }
        queue.subscribe(QUEUE_HIGH, props.getAsync().getHigh().getConcurrency(), this::handle);
        queue.subscribe(QUEUE_LOW, props.getAsync().getLow().getConcurrency(), this::handle);
    }

    public AiTaskSubmitVO submitInterview(Long userId, InterviewGenerateRequest req) {
        return submit(TASK_INTERVIEW, userId, req);
    }

    public AiTaskSubmitVO submitRewrite(Long userId, ResumeAiRequest req) {
        return submit(TASK_REWRITE, userId, req);
    }

    public AiTaskSubmitVO submitExpand(Long userId, ResumeAiRequest req) {
        return submit(TASK_EXPAND, userId, req);
    }

    public AiTaskSubmitVO submitSuggest(Long userId, ResumeAiRequest req) {
        return submit(TASK_SUGGEST, userId, req);
    }

    public AiTaskSubmitVO submitImprove(Long userId, ResumeAiRequest req) {
        return submit(TASK_IMPROVE, userId, req);
    }

    public AiTaskSubmitVO submitScore(Long userId, ResumeScoreRequest req) {
        return submit(TASK_SCORE, userId, req);
    }

    /** 幂等查询任务状态与结果。 */
    public AiTaskVO get(String taskId) {
        AiTaskRecord t = taskStore.get(taskId);
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return toVO(t);
    }

    /**
     * 订阅任务完成通知（SSE 推送用）。若任务已完成则立即回调一次并返回 true；
     * 否则注册回调、任务完成后由 worker 线程触发，返回 false。校验任务归属（防 IDOR）。
     */
    public boolean subscribe(String taskId, Long userId, Consumer<AiTaskVO> onDone) {
        AiTaskRecord t = taskStore.get(taskId);
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (userId == null || !userId.equals(t.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (isDone(t)) {
            onDone.accept(toVO(t));
            return true;
        }
        listeners.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(onDone);
        // 注册与完成之间的竞态二次检查
        if (isDone(t)) {
            List<Consumer<AiTaskVO>> ls = listeners.get(taskId);
            if (ls != null) {
                ls.remove(onDone);
            }
            onDone.accept(toVO(t));
            return true;
        }
        return false;
    }

    private AiTaskVO toVO(AiTaskRecord t) {
        AiTaskVO vo = new AiTaskVO();
        vo.setTaskId(t.getTaskId());
        vo.setTaskType(t.getTaskType());
        vo.setStatus(t.getStatus());
        vo.setError(t.getError());
        if (t.getResultJson() != null && !t.getResultJson().isBlank()) {
            try {
                vo.setResult(om.readTree(t.getResultJson()));
            } catch (Exception ignored) {
                // 结果反序列化失败时保持为 null
            }
        }
        return vo;
    }

    private boolean isDone(AiTaskRecord t) {
        return "SUCCESS".equals(t.getStatus()) || "FAILED".equals(t.getStatus());
    }

    private AiTaskSubmitVO submit(String taskType, Long userId, Object body) {
        if (!props.getAsync().isEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "AI 异步未启用（ai.async.enabled=false）");
        }
        String queueName = queueFor(taskType);
        String taskId = UUID.randomUUID().toString().replace("-", "");
        taskStore.put(new AiTaskRecord(taskId, taskType, userId, "PENDING", null, null));

        AiTaskMessage msg = new AiTaskMessage();
        msg.taskId = taskId;
        msg.taskType = taskType;
        msg.userId = userId;
        msg.body = om.valueToTree(body);
        try {
            queue.publish(queueName, om.writeValueAsString(msg));
        } catch (Exception e) {
            taskStore.remove(taskId);
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "任务入队失败：" + rootMessage(e));
        }
        AiTaskSubmitVO vo = new AiTaskSubmitVO();
        vo.setTaskId(taskId);
        vo.setQueue(queueName);
        vo.setStatus("PENDING");
        return vo;
    }

    void handle(String payload) {
        AiTaskMessage msg;
        try {
            msg = om.readValue(payload, AiTaskMessage.class);
        } catch (Exception e) {
            return;
        }
        AiTaskRecord task = taskStore.get(msg.taskId);
        if (task == null) {
            return;
        }
        // 幂等：消息队列 at-least-once，重复投递（XCLAIM 重投）时若已完成则跳过，避免 AI 重复执行
        if (isDone(task)) {
            return;
        }
        task.setStatus("RUNNING");
        try {
            Object result = dispatch(msg);
            task.setResultJson(om.writeValueAsString(result));
            task.setStatus("SUCCESS");
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setError(rootMessage(e));
        } finally {
            taskStore.put(task); // 持久化最新状态（Redis 实现写回；内存实现存引用）
            notifyListeners(msg.taskId);
        }
    }

    private void notifyListeners(String taskId) {
        List<Consumer<AiTaskVO>> ls = listeners.remove(taskId);
        if (ls == null || ls.isEmpty()) {
            return;
        }
        AiTaskRecord t = taskStore.get(taskId);
        AiTaskVO vo = t == null ? new AiTaskVO() : toVO(t);
        ls.forEach(c -> c.accept(vo));
    }

    private Object dispatch(AiTaskMessage msg) throws Exception {
        switch (msg.taskType) {
            case TASK_INTERVIEW:
                return interviewService.generate(msg.userId, om.treeToValue(msg.body, InterviewGenerateRequest.class));
            case TASK_REWRITE:
                return resumeAiService.rewrite(msg.userId, om.treeToValue(msg.body, ResumeAiRequest.class));
            case TASK_EXPAND:
                return resumeAiService.expand(msg.userId, om.treeToValue(msg.body, ResumeAiRequest.class));
            case TASK_SUGGEST:
                return resumeAiService.suggest(msg.userId, om.treeToValue(msg.body, ResumeAiRequest.class));
            case TASK_IMPROVE:
                return resumeAiService.improve(msg.userId, om.treeToValue(msg.body, ResumeAiRequest.class));
            case TASK_SCORE:
                return resumeAiService.score(msg.userId, om.treeToValue(msg.body, ResumeScoreRequest.class));
            default:
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "未知任务类型：" + msg.taskType);
        }
    }

    private String queueFor(String taskType) {
        switch (taskType) {
            case TASK_INTERVIEW:
            case TASK_SUGGEST:
            case TASK_IMPROVE:
            case TASK_SCORE:
                return QUEUE_HIGH;
            default:
                return QUEUE_LOW;
        }
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    /** 队列消息体（JSON 可序列化，跨进程投递）。 */
    public static class AiTaskMessage {
        public String taskId;
        public String taskType;
        public Long userId;
        public JsonNode body;
    }
}