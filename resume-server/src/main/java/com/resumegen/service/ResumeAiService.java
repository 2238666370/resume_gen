package com.resumegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.ai.AiKnowledgeService;
import com.resumegen.ai.ReActEngine;
import com.resumegen.ai.AiSupport;
import com.resumegen.ai.Skills;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.config.AiProperties;
import com.resumegen.dto.ResumeAiRequest;
import com.resumegen.dto.ResumeApplyRequest;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.dto.ResumeExpandVO;
import com.resumegen.dto.ResumeRewriteVO;
import com.resumegen.dto.ResumeScoreRequest;
import com.resumegen.dto.ResumeScoreVO;
import com.resumegen.dto.ResumeSuggestionVO;
import com.resumegen.entity.AiGenerationLog;
import com.resumegen.mapper.AiGenerationLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI 简历服务（R6）：润色 / STAR 扩写 / 全文建议 / 采纳合并（乐观锁）。
 */
@Service
public class ResumeAiService {

    private final ReActEngine reactEngine;
    private final AiKnowledgeService knowledge;
    private final AiProperties aiProps;
    private final ObjectMapper om;
    private final ResumeService resumeService;
    private final AiGenerationLogMapper logMapper;

    public ResumeAiService(ReActEngine reactEngine, AiKnowledgeService knowledge, AiProperties aiProps,
                           ObjectMapper om, ResumeService resumeService, AiGenerationLogMapper logMapper) {
        this.reactEngine = reactEngine;
        this.knowledge = knowledge;
        this.aiProps = aiProps;
        this.om = om;
        this.resumeService = resumeService;
        this.logMapper = logMapper;
    }

    /** 润色。 */
    public ResumeRewriteVO rewrite(Long userId, ResumeAiRequest req) {
        String text = requireText(req);
        String template = knowledge.skillPrompt(Skills.RESUME_REWRITE, Skills.DEFAULT_REWRITE_PROMPT);
        String prompt = AiSupport.fill(template, Map.of(
                "context", "", "section", nvl(req.getSection()), "text", text));
        ResumeRewriteVO vo = reactEngine.executeObject(
                Skills.SYSTEM_JSON_RULE, prompt, "writing_style", ResumeRewriteVO.class);
        log(userId, toLongOrNull(req.getResumeId()), "rewrite", prompt, toJson(vo));
        return vo;
    }

    /** STAR 扩写。 */
    public ResumeExpandVO expand(Long userId, ResumeAiRequest req) {
        String text = requireText(req);
        String template = knowledge.skillPrompt(Skills.RESUME_EXPAND_STAR, Skills.DEFAULT_EXPAND_PROMPT);
        String prompt = AiSupport.fill(template, Map.of(
                "context", "", "section", nvl(req.getSection()), "text", text));
        ResumeExpandVO vo = reactEngine.executeObject(
                Skills.SYSTEM_JSON_RULE, prompt, "writing_style", ResumeExpandVO.class);
        log(userId, toLongOrNull(req.getResumeId()), "expand", prompt, toJson(vo));
        return vo;
    }

    /** 全文诊断建议（不改稿）。 */
    public List<ResumeSuggestionVO> suggest(Long userId, ResumeAiRequest req) {
        if (isBlank(req.getResumeId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "简历 id 不能为空");
        }
        ResumeDTO dto = resumeService.exportJson(userId, req.getResumeId());
        String resumeJson = AiSupport.desensitizedResumeJson(om, dto, aiProps.getMaxContextChars());
        String template = knowledge.skillPrompt(Skills.RESUME_SUGGEST, Skills.DEFAULT_SUGGEST_PROMPT);
        String prompt = AiSupport.fill(template, Map.of("context", "", "resume", resumeJson));
        List<ResumeSuggestionVO> list = reactEngine.executeList(
                Skills.SYSTEM_JSON_RULE, prompt, "writing_style", ResumeSuggestionVO.class);
        log(userId, toLongOrNull(req.getResumeId()), "suggest", prompt, toJson(list));
        return list;
    }

    /** 全文改稿：生成改进后的完整简历（结构不变），回填脱敏掉的联系方式与结构字段。 */
    public ResumeDTO improve(Long userId, ResumeAiRequest req) {
        if (isBlank(req.getResumeId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "简历 id 不能为空");
        }
        ResumeDTO current = resumeService.exportJson(userId, req.getResumeId());
        String resumeJson = AiSupport.desensitizedResumeJson(om, current, aiProps.getMaxContextChars());
        String template = knowledge.skillPrompt(Skills.RESUME_IMPROVE, Skills.DEFAULT_IMPROVE_PROMPT);
        String prompt = AiSupport.fill(template, Map.of("context", "", "resume", resumeJson));
        ResumeDTO improved = reactEngine.executeObject(
                Skills.SYSTEM_JSON_RULE, prompt, "resume", ResumeDTO.class);
        restorePiiAndStructure(current, improved);
        log(userId, toLongOrNull(req.getResumeId()), "improve", prompt, toJson(improved));
        return improved;
    }

    /** LLM 输出不含脱敏后的联系方式与结构配置，回填原值，避免丢失。 */
    private void restorePiiAndStructure(ResumeDTO src, ResumeDTO dst) {
        if (dst == null) {
            return;
        }
        if (dst.getPersonal() == null) {
            dst.setPersonal(src.getPersonal());
        } else if (src.getPersonal() != null) {
            dst.getPersonal().setPhone(src.getPersonal().getPhone());
            dst.getPersonal().setEmail(src.getPersonal().getEmail());
            dst.getPersonal().setWebsite(src.getPersonal().getWebsite());
            dst.getPersonal().setAvatar(src.getPersonal().getAvatar());
        }
        dst.setTitle(src.getTitle());
        dst.setTemplateId(src.getTemplateId());
        dst.setAccentColor(src.getAccentColor());
        dst.setSections(src.getSections());
    }

    /** 简历评分 + JD 匹配度（ATS 风格 + 可解释建议）。 */
    public ResumeScoreVO score(Long userId, ResumeScoreRequest req) {
        if (isBlank(req.getResumeId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "简历 id 不能为空");
        }
        ResumeDTO dto = resumeService.exportJson(userId, req.getResumeId());
        String resumeJson = AiSupport.desensitizedResumeJson(om, dto, aiProps.getMaxContextChars());
        String template = knowledge.skillPrompt(Skills.RESUME_SCORE, Skills.DEFAULT_SCORE_PROMPT);
        String prompt = AiSupport.fill(template, Map.of(
                "context", "",
                "targetRole", nvl(req.getTargetRole()),
                "jd", nvl(req.getJd()),
                "resume", resumeJson));
        ResumeScoreVO vo = reactEngine.executeObject(
                Skills.SYSTEM_JSON_RULE, prompt, "writing_style", ResumeScoreVO.class);
        log(userId, toLongOrNull(req.getResumeId()), "resume.score", prompt, toJson(vo));
        return vo;
    }

    /** 采纳合并：字段级 patch + 乐观锁回写（复用 PATCH 语义，冲突返回 409）。 */
    public ResumeDetailVO apply(Long userId, ResumeApplyRequest req) {
        if (req.getPatch() == null || req.getPatch().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "patch 不能为空");
        }
        try {
            String body = om.writeValueAsString(req.getPatch());
            return resumeService.patch(userId, req.getResumeId(), body, req.getVersion(), "ai_apply");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "patch 序列化失败");
        }
    }

    private String requireText(ResumeAiRequest req) {
        if (isBlank(req.getText())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "待处理文本不能为空");
        }
        return req.getText().trim();
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

    private String toJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Long toLongOrNull(String id) {
        if (isBlank(id)) {
            return null;
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}