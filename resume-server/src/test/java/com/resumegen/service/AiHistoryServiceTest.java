package com.resumegen.service;

import com.resumegen.ai.AiSupport;
import com.resumegen.common.BusinessException;
import com.resumegen.dto.AiHistoryVO;
import com.resumegen.entity.AiGenerationLog;
import com.resumegen.entity.Resume;
import com.resumegen.mapper.AiGenerationLogMapper;
import com.resumegen.mapper.ResumeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiHistoryServiceTest {

    @Mock private AiGenerationLogMapper logMapper;
    @Mock private ResumeMapper resumeMapper;

    private AiHistoryService service;

    @BeforeEach
    void setUp() {
        service = new AiHistoryService(logMapper, resumeMapper);
    }

    @Test
    void listMapsOutputAndResumeTitle() {
        AiGenerationLog g = new AiGenerationLog();
        g.setId(1L);
        g.setUserId(1L);
        g.setResumeId(9L);
        g.setTaskType("improve");
        g.setOutput("{\"title\":\"改进稿\"}");
        g.setTokenUsage(42);
        g.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(logMapper.selectList(any())).thenReturn(List.of(g));

        Resume r = new Resume();
        r.setId(9L);
        r.setTitle("我的简历");
        when(resumeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(r));

        List<AiHistoryVO> list = service.list(1L, null, null);

        assertThat(list).hasSize(1);
        AiHistoryVO vo = list.get(0);
        assertThat(vo.getId()).isEqualTo("1");
        assertThat(vo.getResumeTitle()).isEqualTo("我的简历");
        assertThat(vo.getTaskType()).isEqualTo("improve");
        assertThat(vo.getSummary()).isEqualTo("{\"title\":\"改进稿\"}");
        assertThat(vo.getOutput()).isNull();
        assertThat(vo.getCreatedAt()).startsWith("2026-01-01");
    }

    @Test
    void detailNotFoundThrows404() {
        when(logMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.detail(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void detailReturnsFullOutput() {
        AiGenerationLog g = new AiGenerationLog();
        g.setId(2L);
        g.setUserId(1L);
        g.setTaskType("rewrite");
        g.setOutput("{\"revised\":\"xx\"}");
        when(logMapper.selectOne(any())).thenReturn(g);

        AiHistoryVO vo = service.detail(1L, 2L);

        assertThat(vo.getOutput()).isEqualTo("{\"revised\":\"xx\"}");
        assertThat(vo.getSummary()).isNull();
    }
}