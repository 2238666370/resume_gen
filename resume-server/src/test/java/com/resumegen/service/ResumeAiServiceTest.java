package com.resumegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.ai.AiKnowledgeService;
import com.resumegen.ai.ReActEngine;
import com.resumegen.ai.Skills;
import com.resumegen.common.BusinessException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResumeAiServiceTest {

    @Mock private ReActEngine reactEngine;
    @Mock private AiKnowledgeService knowledge;
    @Mock private ResumeService resumeService;
    @Mock private AiGenerationLogMapper logMapper;

    private final ObjectMapper om = new ObjectMapper();
    private final AiProperties aiProps = new AiProperties();
    private ResumeAiService service;

    @BeforeEach
    void setUp() {
        service = new ResumeAiService(reactEngine, knowledge, aiProps, om, resumeService, logMapper);
        lenient().when(knowledge.retrieve(anyString(), anyString())).thenReturn("");
        lenient().when(knowledge.collect(anyString(), anyInt())).thenReturn("");
        lenient().when(knowledge.skillPrompt(anyString(), anyString())).thenReturn(Skills.DEFAULT_REWRITE_PROMPT);
    }

    @Test
    void rewriteReturnsResult() {
        ResumeRewriteVO vo = new ResumeRewriteVO();
        vo.setOriginal("原文");
        vo.setRevised("润色后");
        when(reactEngine.executeObject(anyString(), anyString(), anyString(), eq(ResumeRewriteVO.class))).thenReturn(vo);

        ResumeAiRequest req = new ResumeAiRequest();
        req.setText("我做了很多事");

        ResumeRewriteVO result = service.rewrite(1L, req);
        assertThat(result.getRevised()).isEqualTo("润色后");
        verify(logMapper).insert(any(AiGenerationLog.class));
    }

    @Test
    void rewriteMissingTextReturns400() {
        ResumeAiRequest req = new ResumeAiRequest();
        assertThatThrownBy(() -> service.rewrite(1L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
    }

    @Test
    void expandReturnsResult() {
        ResumeExpandVO vo = new ResumeExpandVO();
        vo.setOriginal("要点");
        vo.setExpanded("扩写");
        when(reactEngine.executeObject(anyString(), anyString(), anyString(), eq(ResumeExpandVO.class))).thenReturn(vo);

        ResumeAiRequest req = new ResumeAiRequest();
        req.setText("要点");

        ResumeExpandVO result = service.expand(1L, req);
        assertThat(result.getExpanded()).isEqualTo("扩写");
    }

    @Test
    void suggestRequiresResumeId() {
        ResumeAiRequest req = new ResumeAiRequest();
        assertThatThrownBy(() -> service.suggest(1L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
    }

    @Test
    void suggestReturnsList() {
        ResumeDTO dto = new ResumeDTO();
        dto.getPersonal().setName("张三");
        when(resumeService.exportJson(1L, "9")).thenReturn(dto);

        ResumeSuggestionVO s = new ResumeSuggestionVO();
        s.setSection("experience");
        s.setIssue("问题");
        s.setAdvice("建议");
        s.setPriority("高");
        when(reactEngine.executeList(anyString(), anyString(), anyString(), eq(ResumeSuggestionVO.class)))
                .thenReturn(List.of(s));

        ResumeAiRequest req = new ResumeAiRequest();
        req.setResumeId("9");

        List<ResumeSuggestionVO> list = service.suggest(1L, req);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getSection()).isEqualTo("experience");
    }

    @Test
    void scoreRequiresResumeId() {
        ResumeScoreRequest req = new ResumeScoreRequest();
        assertThatThrownBy(() -> service.score(1L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
    }

    @Test
    void scoreReturnsResult() {
        ResumeDTO dto = new ResumeDTO();
        dto.getPersonal().setName("张三");
        when(resumeService.exportJson(1L, "9")).thenReturn(dto);

        ResumeScoreVO vo = new ResumeScoreVO();
        vo.setTotalScore(82);
        vo.setMatchedPercent(68);
        ResumeScoreVO.Dimension d = new ResumeScoreVO.Dimension();
        d.setKey("completeness");
        d.setName("完整性");
        d.setScore(85);
        vo.setDimensions(List.of(d));
        when(reactEngine.executeObject(anyString(), anyString(), anyString(), eq(ResumeScoreVO.class)))
                .thenReturn(vo);

        ResumeScoreRequest req = new ResumeScoreRequest();
        req.setResumeId("9");
        req.setJd("需要 redis");

        ResumeScoreVO result = service.score(1L, req);
        assertThat(result.getTotalScore()).isEqualTo(82);
        assertThat(result.getMatchedPercent()).isEqualTo(68);
        assertThat(result.getDimensions()).hasSize(1);
        verify(logMapper).insert(any(AiGenerationLog.class));
    }

    @Test
    void applyPassesVersionToPatch() {
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId("9");
        vo.setVersion(2);
        when(resumeService.patch(eq(1L), eq("9"), anyString(), eq(2), eq("ai_apply"))).thenReturn(vo);

        ResumeApplyRequest req = new ResumeApplyRequest();
        req.setResumeId("9");
        req.setVersion(2);
        req.setPatch(Map.of("experience", List.of()));

        ResumeDetailVO result = service.apply(1L, req);
        assertThat(result.getId()).isEqualTo("9");
    }

    @Test
    void applyEmptyPatchReturns400() {
        ResumeApplyRequest req = new ResumeApplyRequest();
        req.setResumeId("9");
        req.setPatch(Map.of());

        assertThatThrownBy(() -> service.apply(1L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
    }

    @Test
    void applyConflictPropagates409() {
        when(resumeService.patch(eq(1L), eq("9"), anyString(), eq(1), eq("ai_apply")))
                .thenThrow(new BusinessException(409, "冲突"));

        ResumeApplyRequest req = new ResumeApplyRequest();
        req.setResumeId("9");
        req.setVersion(1);
        req.setPatch(Map.of("personal", Map.of()));

        assertThatThrownBy(() -> service.apply(1L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);
    }
}