package com.resumegen.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.entity.CommunityPost;
import com.resumegen.entity.Resume;
import com.resumegen.mapper.CommunityPostMapper;
import com.resumegen.mapper.ResumeMapper;
import com.resumegen.mq.MessageQueue;
import com.resumegen.repository.ResumeRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 检索索引同步（R10-O4）：简历/社区帖写入或删除后，异步入队（复用 O1 消息队列）
 * 由消费者同步到 ES，最终一致；ES 未启用（engine=mysql）时全部 no-op。
 *
 * <p>失败仅记录日志，不影响主链路；ES 可用时可全量重建索引补偿。
 */
@Component
public class SearchIndexSyncService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexSyncService.class);
    /** 索引同步队列。 */
    public static final String QUEUE = "search";
    private static final int CONCURRENCY = 2;

    private final SearchService searchService;
    private final MessageQueue queue;
    private final ResumeRepository resumeRepository;
    private final ResumeMapper resumeMapper;
    private final CommunityPostMapper postMapper;
    private final ObjectMapper om;

    /** 全量重建索引的守护线程（启动时触发一次）。 */
    private final ExecutorService reindexExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "es-reindex");
        t.setDaemon(true);
        return t;
    });

    public SearchIndexSyncService(SearchService searchService, MessageQueue queue,
                                  ResumeRepository resumeRepository, ResumeMapper resumeMapper,
                                  CommunityPostMapper postMapper, ObjectMapper om) {
        this.searchService = searchService;
        this.queue = queue;
        this.resumeRepository = resumeRepository;
        this.resumeMapper = resumeMapper;
        this.postMapper = postMapper;
        this.om = om;
    }

    @PostConstruct
    void start() {
        if (!searchService.active()) {
            return;
        }
        queue.subscribe(QUEUE, CONCURRENCY, this::handle);
        // 启动时全量重建（幂等 upsert），避免切换 ES 后既有数据在检索中「消失」
        reindexExecutor.submit(this::reindexAll);
    }

    @PreDestroy
    void shutdown() {
        reindexExecutor.shutdownNow();
    }

    /** 全量重建索引：存量简历 + 上线社区帖（按 id upsert，幂等可重跑）。 */
    void reindexAll() {
        try {
            List<Resume> resumes = resumeMapper.selectList(
                    new LambdaQueryWrapper<Resume>().eq(Resume::getStatus, 1));
            for (Resume r : resumes) {
                try {
                    ResumeDetailVO vo = resumeRepository.get(r.getUserId(), String.valueOf(r.getId()));
                    searchService.indexResume(r.getUserId(), r.getId(), vo);
                } catch (Exception e) {
                    log.warn("重建简历索引失败 resumeId={}：{}", r.getId(), e.getMessage());
                }
            }
            List<CommunityPost> posts = postMapper.selectList(
                    new LambdaQueryWrapper<CommunityPost>()
                            .eq(CommunityPost::getAuditStatus, CommunityPost.ST_ONLINE));
            for (CommunityPost p : posts) {
                searchService.indexPost(p);
            }
            log.info("ES 全量重建索引完成：简历 {} 份，社区帖 {} 条", resumes.size(), posts.size());
        } catch (Exception e) {
            log.warn("ES 全量重建索引失败（检索仍可用，可重启重试）：{}", e.getMessage());
        }
    }

    // ---------- 发布（写入链路调用） ----------

    public void syncResume(Long userId, Long resumeId) {
        publish(new Message("resume", "index", userId, resumeId));
    }

    public void syncDeleteResume(Long resumeId) {
        publish(new Message("resume", "delete", null, resumeId));
    }

    public void syncPost(Long postId) {
        publish(new Message("post", "index", null, postId));
    }

    public void syncDeletePost(Long postId) {
        publish(new Message("post", "delete", null, postId));
    }

    // ---------- 消费（同步到 ES） ----------

    void handle(String payload) {
        try {
            Message msg = om.readValue(payload, Message.class);
            if ("resume".equals(msg.getType())) {
                if ("delete".equals(msg.getAction())) {
                    searchService.deleteResume(msg.getId());
                } else {
                    ResumeDetailVO vo = resumeRepository.get(msg.getUserId(), String.valueOf(msg.getId()));
                    searchService.indexResume(msg.getUserId(), msg.getId(), vo);
                }
            } else if ("post".equals(msg.getType())) {
                if ("delete".equals(msg.getAction())) {
                    searchService.deletePost(msg.getId());
                } else {
                    CommunityPost post = postMapper.selectById(msg.getId());
                    if (post != null) {
                        searchService.indexPost(post);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("ES 索引同步失败：{}", e.getMessage());
        }
    }

    private void publish(Message msg) {
        if (!searchService.active()) {
            return;
        }
        try {
            queue.publish(QUEUE, om.writeValueAsString(msg));
        } catch (Exception e) {
            log.warn("ES 索引同步入队失败：{}", e.getMessage());
        }
    }

    /** 索引同步消息。 */
    @Data
    public static class Message {
        private String type;
        private String action;
        private Long userId;
        private Long id;

        public Message() {
        }

        public Message(String type, String action, Long userId, Long id) {
            this.type = type;
            this.action = action;
            this.userId = userId;
            this.id = id;
        }
    }
}
