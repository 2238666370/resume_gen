package com.resumegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.common.BusinessException;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.dto.ResumeSnapshotVO;
import com.resumegen.dto.ResumeVersionDiffVO;
import com.resumegen.entity.ResumeSnapshot;
import com.resumegen.mapper.ResumeSnapshotMapper;
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
class ResumeSnapshotServiceTest {

    @Mock
    private ResumeSnapshotMapper snapshotMapper;

    private final ObjectMapper om = new ObjectMapper();
    private ResumeSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new ResumeSnapshotService(snapshotMapper, om);
    }

    private ResumeSnapshot snapshot(long id, long resumeId, int version, String contentJson) {
        ResumeSnapshot s = new ResumeSnapshot();
        s.setId(id);
        s.setUserId(1L);
        s.setResumeId(resumeId);
        s.setVersion(version);
        s.setSource("manual");
        s.setContent(contentJson);
        s.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        return s;
    }

    @Test
    void recordSerializesContentAndInserts() {
        ResumeDTO dto = new ResumeDTO();
        dto.setTitle("测试");
        service.record(1L, "9", 1, "manual", dto);

        ArgumentCaptor<ResumeSnapshot> cap = ArgumentCaptor.forClass(ResumeSnapshot.class);
        verify(snapshotMapper).insert(cap.capture());
        assertThat(cap.getValue().getResumeId()).isEqualTo(9L);
        assertThat(cap.getValue().getVersion()).isEqualTo(1);
        assertThat(cap.getValue().getContent()).contains("\"title\":\"测试\"");
    }

    @Test
    void recordFailureIsSwallowed() {
        when(snapshotMapper.insert(any())).thenThrow(new RuntimeException("db down"));
        service.record(1L, "9", 1, "manual", new ResumeDTO());
        verify(snapshotMapper).insert(any());
    }

    @Test
    void listMapsVersionsWithoutContent() {
        when(snapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(snapshot(1L, 9L, 2, "{}")));

        List<ResumeSnapshotVO> list = service.list(1L, "9");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getVersion()).isEqualTo(2);
        assertThat(list.get(0).getContent()).isNull();
    }

    @Test
    void getNotOwnedThrows404() {
        when(snapshotMapper.selectById(5L)).thenReturn(null);
        assertThatThrownBy(() -> service.get(1L, "9", 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void getCrossUserThrows404() {
        ResumeSnapshot s = snapshot(1L, 9L, 1, "{}");
        s.setUserId(2L);
        when(snapshotMapper.selectById(1L)).thenReturn(s);
        assertThatThrownBy(() -> service.get(1L, "9", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void diffReturnsBothContents() throws Exception {
        ResumeDTO from = new ResumeDTO();
        from.setTitle("旧");
        ResumeDTO to = new ResumeDTO();
        to.setTitle("新");
        when(snapshotMapper.selectById(1L)).thenReturn(snapshot(1L, 9L, 1, om.writeValueAsString(from)));
        when(snapshotMapper.selectById(2L)).thenReturn(snapshot(2L, 9L, 2, om.writeValueAsString(to)));

        ResumeVersionDiffVO diff = service.diff(1L, "9", 1L, 2L);
        assertThat(diff.getFromVersion()).isEqualTo(1);
        assertThat(diff.getToVersion()).isEqualTo(2);
        assertThat(diff.getFrom().getTitle()).isEqualTo("旧");
        assertThat(diff.getTo().getTitle()).isEqualTo("新");
    }
}
