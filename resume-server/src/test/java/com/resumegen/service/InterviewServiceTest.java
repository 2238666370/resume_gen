package com.resumegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.ai.AiKnowledgeService;
import com.resumegen.ai.ReActEngine;
import com.resumegen.ai.Skills;
import com.resumegen.common.BusinessException;
import com.resumegen.config.AiProperties;
import com.resumegen.dto.InterviewGenerateRequest;
import com.resumegen.dto.InterviewQuestionVO;
import com.resumegen.dto.InterviewSetVO;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.entity.AiGenerationLog;
import com.resumegen.entity.InterviewSet;
import com.resumegen.mapper.AiGenerationLogMapper;
import com.resumegen.mapper.InterviewSetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewServiceTest {

    @Mock private ReActEngine reactEngine;
    @Mock private AiKnowledgeService knowledge;
    @Mock private ResumeService resumeService;
    @Mock private InterviewSetMapper setMapper;
    @Mock private AiGenerationLogMapper logMapper;

    private final ObjectMapper om = new ObjectMapper();
    private final AiProperties aiProps = new AiProperties();
    private InterviewService service;

    @BeforeEach
    void setUp() {
        service = new InterviewService(reactEngine, knowledge, aiProps, om, resumeService, setMapper, logMapper);
        lenient().when(knowledge.retrieve(anyString(), anyString())).thenReturn("");
        lenient().when(knowledge.collect(anyString(), anyInt())).thenReturn("");
        lenient().when(knowledge.skillPrompt(anyString(), anyString())).thenReturn(Skills.DEFAULT_INTERVIEW_PROMPT);
    }

    private ResumeDTO resumeWithContact() {
        ResumeDTO dto = new ResumeDTO();
        dto.setTitle("测试简历");
        dto.getPersonal().setName("张三");
        dto.getPersonal().setEmail("a@b.c");
        dto.getPersonal().setPhone("13800000000");
        dto.getPersonal().setSummary("资深后端工程师");
        return dto;
    }

    @Test
    void generateStripsContactAndPersists() {
        when(resumeService.exportJson(1L, "9")).thenReturn(resumeWithContact());

        InterviewQuestionVO q = new InterviewQuestionVO();
        q.setCategory("项目深挖");
        q.setQuestion("问题");
        q.setAnswer("答案");
        q.setTips("重点");
        when(reactEngine.executeList(anyString(), anyString(), anyString(), eq(InterviewQuestionVO.class)))
                .thenReturn(List.of(q));

        InterviewGenerateRequest req = new InterviewGenerateRequest();
        req.setResumeId("9");
        req.setTargetRole("Java后端");

        InterviewSetVO vo = service.generate(1L, req);

        assertThat(vo.getResumeId()).isEqualTo("9");
        assertThat(vo.getQuestions()).hasSize(1);
        assertThat(vo.getTitle()).startsWith("测试简历 · ");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(reactEngine).executeList(anyString(), promptCaptor.capture(), anyString(), eq(InterviewQuestionVO.class));
        assertThat(promptCaptor.getValue())
                .contains("Java后端")
                .doesNotContain("a@b.c")
                .doesNotContain("13800000000");

        verify(setMapper).insert(any(InterviewSet.class));
        verify(logMapper).insert(any(AiGenerationLog.class));
    }

    @Test
    void generatePropagatesAiError() {
        when(resumeService.exportJson(1L, "9")).thenReturn(resumeWithContact());
        when(reactEngine.executeList(anyString(), anyString(), anyString(), eq(InterviewQuestionVO.class)))
                .thenThrow(new BusinessException(500, "AI 不可用"));

        InterviewGenerateRequest req = new InterviewGenerateRequest();
        req.setResumeId("9");

        assertThatThrownBy(() -> service.generate(1L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(500);
        verify(setMapper, never()).insert(any());
    }

    @Test
    void listMapsCreatedAt() {
        InterviewSet s = new InterviewSet();
        s.setId(1L);
        s.setUserId(1L);
        s.setResumeId(9L);
        s.setTitle("题集");
        s.setQuestions("[{\"category\":\"行为面\",\"question\":\"q\",\"answer\":\"a\",\"tips\":\"t\"}]");
        s.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(setMapper.selectList(any())).thenReturn(List.of(s));

        List<InterviewSetVO> list = service.list(1L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getQuestions()).hasSize(1);
        assertThat(list.get(0).getCreatedAt()).startsWith("2026-01-01");
    }

    @Test
    void getNotOwnedThrows404() {
        InterviewSet s = new InterviewSet();
        s.setId(1L);
        s.setUserId(2L);
        when(setMapper.selectById(1L)).thenReturn(s);

        assertThatThrownBy(() -> service.get(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void deleteOwnedRemoves() {
        InterviewSet s = new InterviewSet();
        s.setId(1L);
        s.setUserId(1L);
        when(setMapper.selectById(1L)).thenReturn(s);

        service.delete(1L, 1L);
        verify(setMapper).deleteById(1L);
    }

    @Test
    void renameUpdatesTitle() {
        InterviewSet s = new InterviewSet();
        s.setId(1L);
        s.setUserId(1L);
        s.setTitle("旧名");
        s.setQuestions("[]");
        when(setMapper.selectById(1L)).thenReturn(s);

        InterviewSetVO vo = service.rename(1L, 1L, " 新名字 ");

        assertThat(vo.getTitle()).isEqualTo("新名字");
        verify(setMapper).updateById(s);
    }

    @Test
    void renameBlankThrows400() {
        assertThatThrownBy(() -> service.rename(1L, 1L, "   "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
    }
}