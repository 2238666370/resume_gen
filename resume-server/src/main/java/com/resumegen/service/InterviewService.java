package com.resumegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.ai.AiKnowledgeService;
import com.resumegen.ai.ReActEngine;
import com.resumegen.ai.AiSupport;
import com.resumegen.ai.Skills;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.config.AiProperties;
import com.resumegen.dto.InterviewGenerateRequest;
import com.resumegen.dto.InterviewQuestionVO;
import com.resumegen.dto.InterviewSetVO;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.entity.AiGenerationLog;
import com.resumegen.entity.InterviewSet;
import com.resumegen.mapper.AiGenerationLogMapper;
import com.resumegen.mapper.InterviewSetMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 面试题库服务（R5）：RAG 检索 + Skill 模板 → LLM 生成结构化题库，结果持久化。
 */
@Service
public class InterviewService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TITLE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReActEngine reactEngine;
    private final AiKnowledgeService knowledge;
    private final AiProperties aiProps;
    private final ObjectMapper om;
    private final ResumeService resumeService;
    private final InterviewSetMapper setMapper;
    private final AiGenerationLogMapper logMapper;

    public InterviewService(ReActEngine reactEngine, AiKnowledgeService knowledge, AiProperties aiProps,
                            ObjectMapper om, ResumeService resumeService,
                            InterviewSetMapper setMapper, AiGenerationLogMapper logMapper) {
        this.reactEngine = reactEngine;
        this.knowledge = knowledge;
        this.aiProps = aiProps;
        this.om = om;
        this.resumeService = resumeService;
        this.setMapper = setMapper;
        this.logMapper = logMapper;
    }

    /** 生成题库并保存。 */
    public InterviewSetVO generate(Long userId, InterviewGenerateRequest req) {
        ResumeDTO dto = resumeService.exportJson(userId, req.getResumeId());
        String resumeJson = AiSupport.desensitizedResumeJson(om, dto, aiProps.getMaxContextChars());

        String template = knowledge.skillPrompt(Skills.INTERVIEW_GEN_QUESTIONS, Skills.DEFAULT_INTERVIEW_PROMPT);
        String task = AiSupport.fill(template, Map.of(
                "context", "",
                "targetRole", nvl(req.getTargetRole()),
                "jd", nvl(req.getJd()),
                "resume", resumeJson));

        List<InterviewQuestionVO> questions = reactEngine.executeList(
                Skills.SYSTEM_JSON_RULE, task, "interview_q", InterviewQuestionVO.class);

        String title = buildTitle(dto);
        InterviewSet set = new InterviewSet();
        set.setUserId(userId);
        set.setResumeId(parseResumeId(req.getResumeId()));
        set.setTitle(title);
        set.setTargetRole(req.getTargetRole());
        try {
            set.setQuestions(om.writeValueAsString(questions));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "题库序列化失败");
        }
        setMapper.insert(set);

        log(userId, set.getResumeId(), "interview", task, set.getQuestions());

        InterviewSetVO vo = new InterviewSetVO();
        vo.setId(String.valueOf(set.getId()));
        vo.setResumeId(req.getResumeId());
        vo.setTitle(title);
        vo.setTargetRole(req.getTargetRole());
        vo.setQuestions(questions);
        return vo;
    }

    /** 我的题集列表。 */
    public List<InterviewSetVO> list(Long userId) {
        List<InterviewSet> sets = setMapper.selectList(new LambdaQueryWrapper<InterviewSet>()
                .eq(InterviewSet::getUserId, userId)
                .orderByDesc(InterviewSet::getId));
        List<InterviewSetVO> result = new ArrayList<>();
        for (InterviewSet s : sets) {
            result.add(toVO(s));
        }
        return result;
    }

    /** 题集详情（校验归属）。 */
    public InterviewSetVO get(Long userId, Long id) {
        InterviewSet s = owned(userId, id);
        return toVO(s);
    }

    /** 删除题集（校验归属）。 */
    public void delete(Long userId, Long id) {
        InterviewSet s = owned(userId, id);
        setMapper.deleteById(s.getId());
    }

    /** 重命名题集（校验归属）。 */
    public InterviewSetVO rename(Long userId, Long id, String title) {
        if (isBlank(title)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "题集名称不能为空");
        }
        String trimmed = title.trim();
        if (trimmed.length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "题集名称不能超过 100 字");
        }
        InterviewSet s = owned(userId, id);
        s.setTitle(trimmed);
        setMapper.updateById(s);
        return toVO(s);
    }

    private InterviewSet owned(Long userId, Long id) {
        InterviewSet s = setMapper.selectById(id);
        if (s == null || !userId.equals(s.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return s;
    }

    private InterviewSetVO toVO(InterviewSet s) {
        InterviewSetVO vo = new InterviewSetVO();
        vo.setId(String.valueOf(s.getId()));
        vo.setResumeId(s.getResumeId() == null ? null : String.valueOf(s.getResumeId()));
        vo.setTitle(s.getTitle());
        vo.setTargetRole(s.getTargetRole());
        vo.setQuestions(AiSupport.parseList(om, s.getQuestions(), InterviewQuestionVO.class));
        vo.setCreatedAt(s.getCreatedAt() == null ? null : s.getCreatedAt().format(FMT));
        return vo;
    }

    private void log(Long userId, Long resumeId, String taskType, String input, String output) {
        AiGenerationLog g = new AiGenerationLog();
        g.setUserId(userId);
        g.setResumeId(resumeId);
        g.setTaskType(taskType);
        g.setInputHash(AiSupport.sha256(input));
        g.setOutput(output);
        g.setTokenUsage(AiSupport.estimateTokens(input, output));
        g.setStatus(1);
        logMapper.insert(g);
    }

    /** 默认题集名：简历名称 + 生成时间。 */
    private String buildTitle(ResumeDTO dto) {
        String name = isBlank(dto == null ? null : dto.getTitle()) ? "未命名简历" : dto.getTitle().trim();
        return name + " · " + LocalDateTime.now().format(TITLE_FMT);
    }

    private Long parseResumeId(String resumeId) {
        try {
            return Long.parseLong(resumeId);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "简历 id 格式错误");
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}