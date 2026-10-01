package com.resumegen.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.cache.CacheService;
import com.resumegen.common.BusinessException;
import com.resumegen.common.PageResult;
import com.resumegen.config.ResumeProperties;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.dto.ResumeListItemVO;
import com.resumegen.repository.ResumeRepository;
import com.resumegen.search.SearchIndexSyncService;
import com.resumegen.search.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock
    private ResumeRepository repository;
    @Mock
    private CacheService cacheService;
    @Mock
    private ProfileService profileService;
    @Mock
    private ResumeSnapshotService snapshotService;
    @Mock
    private SearchService searchService;
    @Mock
    private SearchIndexSyncService searchIndexSync;

    // 与 Spring Boot 注入的 ObjectMapper 行为对齐：忽略未知属性
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private ResumeService resumeService;

    @BeforeEach
    void setUp() {
        ResumeProperties props = new ResumeProperties();
        resumeService = new ResumeService(repository, cacheService, props, objectMapper, profileService,
                snapshotService, searchService, searchIndexSync);
    }

    @Test
    void listDelegatesToRepository() {
        when(repository.count(1L, null)).thenReturn(2L);
        ResumeListItemVO item = new ResumeListItemVO();
        item.setId("r1");
        item.setTitle("t");
        when(repository.list(1L, 1, 10, null)).thenReturn(List.of(item));

        PageResult<ResumeListItemVO> result = resumeService.list(1L, 1, 10, null);

        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getId()).isEqualTo("r1");
    }

    @Test
    void getCacheMissHitsRepositoryAndWritesCache() throws Exception {
        when(cacheService.get(any())).thenReturn(null);
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId("r1");
        vo.setTitle("t");
        when(repository.get(1L, "r1")).thenReturn(vo);

        ResumeDetailVO result = resumeService.get(1L, "r1");

        assertThat(result.getId()).isEqualTo("r1");
        verify(cacheService).set(any(), any(), anyLong());
    }

    @Test
    void getCacheHitSkipsRepository() throws Exception {
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId("r1");
        vo.setTitle("t");
        vo.setVersion(2);
        String json = objectMapper.writeValueAsString(vo);
        when(cacheService.get(any())).thenReturn(json);

        ResumeDetailVO result = resumeService.get(1L, "r1");

        assertThat(result.getTitle()).isEqualTo("t");
        assertThat(result.getVersion()).isEqualTo(2);
        verify(repository, never()).get(any(), any());
    }

    @Test
    void createAppliesDefaultTitleAndSections() {
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId("r1");
        when(repository.create(eq(1L), any(ResumeDTO.class))).thenReturn(vo);

        resumeService.create(1L, null, null);

        ArgumentCaptor<ResumeDTO> cap = ArgumentCaptor.forClass(ResumeDTO.class);
        verify(repository).create(eq(1L), cap.capture());
        assertThat(cap.getValue().getTitle()).isEqualTo("未命名简历");
        assertThat(cap.getValue().getSections()).hasSize(8);
    }

    @Test
    void updateSuccessDeletesCache() {
        ResumeDTO dto = new ResumeDTO();
        when(repository.update(1L, "r1", dto, 3)).thenReturn(true);
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId("r1");
        when(repository.get(1L, "r1")).thenReturn(vo);

        ResumeDetailVO result = resumeService.update(1L, "r1", dto, 3);

        assertThat(result).isSameAs(vo);
        verify(cacheService).delete(any());
    }

    @Test
    void updateConflictThrowsConflict() {
        ResumeDTO dto = new ResumeDTO();
        when(repository.update(1L, "r1", dto, 3)).thenReturn(false);

        assertThatThrownBy(() -> resumeService.update(1L, "r1", dto, 3))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);
    }

    @Test
    void deleteNotFoundThrowsNotFound() {
        when(repository.delete(1L, "r1")).thenReturn(false);

        assertThatThrownBy(() -> resumeService.delete(1L, "r1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void deleteSuccessDeletesCache() {
        when(repository.delete(1L, "r1")).thenReturn(true);

        resumeService.delete(1L, "r1");

        verify(cacheService).delete(any());
    }

    @Test
    void exportJsonStripsMetadata() {
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId("r1");
        vo.setVersion(5);
        vo.setTitle("我的简历");
        when(repository.get(1L, "r1")).thenReturn(vo);

        ResumeDTO dto = resumeService.exportJson(1L, "r1");

        assertThat(dto.getTitle()).isEqualTo("我的简历");
    }

    @Test
    void importJsonCreatesNewResume() {
        ResumeDTO dto = new ResumeDTO();
        dto.setTitle("导入简历");
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId("r9");
        when(repository.create(1L, dto)).thenReturn(vo);

        ResumeDetailVO result = resumeService.importJson(1L, dto);

        assertThat(result.getId()).isEqualTo("r9");
        verify(repository).create(1L, dto);
    }
}