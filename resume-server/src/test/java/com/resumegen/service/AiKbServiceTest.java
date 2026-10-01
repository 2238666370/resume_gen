package com.resumegen.service;

import com.resumegen.ai.AiKnowledgeService;
import com.resumegen.common.BusinessException;
import com.resumegen.dto.AiKbEntryRequest;
import com.resumegen.dto.AiKbEntryVO;
import com.resumegen.entity.AiKbEntry;
import com.resumegen.mapper.AiKbEntryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiKbServiceTest {

    @Mock private AiKbEntryMapper kbMapper;
    @Mock private AiKnowledgeService knowledgeService;

    private AiKbService service;

    @BeforeEach
    void setUp() {
        service = new AiKbService(kbMapper, knowledgeService);
    }

    @Test
    void createAppliesDefaultStatusAndInvalidatesIndex() {
        AiKbEntryRequest req = new AiKbEntryRequest();
        req.setKbType("writing_style");
        req.setCode("w_x");
        req.setContent("内容");

        service.create(req);

        ArgumentCaptor<AiKbEntry> captor = ArgumentCaptor.forClass(AiKbEntry.class);
        verify(kbMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        assertThat(captor.getValue().getKbType()).isEqualTo("writing_style");
        verify(knowledgeService).invalidate();
    }

    @Test
    void updateMissingReturns404() {
        when(kbMapper.selectById(9L)).thenReturn(null);

        AiKbEntryRequest req = new AiKbEntryRequest();
        req.setKbType("writing_style");
        req.setContent("内容");

        assertThatThrownBy(() -> service.update(9L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void listFormatsDate() {
        AiKbEntry e = new AiKbEntry();
        e.setId(1L);
        e.setKbType("interview_q");
        e.setCode("q1");
        e.setContent("内容");
        e.setStatus(1);
        e.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        when(kbMapper.selectList(any())).thenReturn(List.of(e));

        List<AiKbEntryVO> list = service.list("interview_q");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo("1");
        assertThat(list.get(0).getCreatedAt()).startsWith("2026-01-02");
    }

    @Test
    void deleteInvalidatesIndex() {
        AiKbEntry e = new AiKbEntry();
        e.setId(1L);
        when(kbMapper.selectById(1L)).thenReturn(e);

        service.delete(1L);
        verify(kbMapper).deleteById(1L);
        verify(knowledgeService).invalidate();
    }
}