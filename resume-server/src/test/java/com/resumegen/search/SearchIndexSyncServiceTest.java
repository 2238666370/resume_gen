package com.resumegen.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.entity.CommunityPost;
import com.resumegen.mapper.CommunityPostMapper;
import com.resumegen.mapper.ResumeMapper;
import com.resumegen.mq.MessageQueue;
import com.resumegen.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexSyncServiceTest {

    @Mock
    private SearchService searchService;
    @Mock
    private MessageQueue queue;
    @Mock
    private ResumeRepository resumeRepository;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private CommunityPostMapper postMapper;

    private final ObjectMapper om = new ObjectMapper().findAndRegisterModules();
    private SearchIndexSyncService service;

    @BeforeEach
    void setUp() {
        service = new SearchIndexSyncService(searchService, queue, resumeRepository, resumeMapper, postMapper, om);
    }

    @Test
    void inactiveDoesNotSubscribeNorPublish() {
        when(searchService.active()).thenReturn(false);
        service.start();
        service.syncResume(1L, 9L);

        verify(queue, never()).subscribe(anyString(), anyInt(), any());
        verify(queue, never()).publish(anyString(), anyString());
    }

    @Test
    void activeSubscribesAndPublishesResumeIndex() throws Exception {
        when(searchService.active()).thenReturn(true);
        service.start();
        verify(queue).subscribe(eq(SearchIndexSyncService.QUEUE), anyInt(), any());

        service.syncResume(1L, 9L);

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(queue).publish(eq(SearchIndexSyncService.QUEUE), cap.capture());
        SearchIndexSyncService.Message msg = om.readValue(cap.getValue(), SearchIndexSyncService.Message.class);
        assertThat(msg.getType()).isEqualTo("resume");
        assertThat(msg.getAction()).isEqualTo("index");
        assertThat(msg.getId()).isEqualTo(9L);
    }

    @Test
    void reindexAllIndexesExistingResumesAndPosts() {
        com.resumegen.entity.Resume r = new com.resumegen.entity.Resume();
        r.setId(9L);
        r.setUserId(1L);
        r.setStatus(1);
        when(resumeMapper.selectList(any())).thenReturn(java.util.List.of(r));
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId("9");
        when(resumeRepository.get(1L, "9")).thenReturn(vo);
        CommunityPost post = new CommunityPost();
        post.setId(5L);
        when(postMapper.selectList(any())).thenReturn(java.util.List.of(post));

        service.reindexAll();

        verify(searchService).indexResume(1L, 9L, vo);
        verify(searchService).indexPost(post);
    }

    @Test
    void handleIndexesResumeFromRepository() throws Exception {
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId("9");
        when(resumeRepository.get(1L, "9")).thenReturn(vo);

        SearchIndexSyncService.Message msg =
                new SearchIndexSyncService.Message("resume", "index", 1L, 9L);
        service.handle(om.writeValueAsString(msg));

        verify(searchService).indexResume(1L, 9L, vo);
    }

    @Test
    void handleDeleteResume() throws Exception {
        SearchIndexSyncService.Message msg =
                new SearchIndexSyncService.Message("resume", "delete", null, 9L);
        service.handle(om.writeValueAsString(msg));

        verify(searchService).deleteResume(9L);
    }

    @Test
    void handleIndexesPostFromMapper() throws Exception {
        CommunityPost post = new CommunityPost();
        post.setId(5L);
        when(postMapper.selectById(5L)).thenReturn(post);

        SearchIndexSyncService.Message msg =
                new SearchIndexSyncService.Message("post", "index", null, 5L);
        service.handle(om.writeValueAsString(msg));

        verify(searchService).indexPost(post);
    }

    @Test
    void handleSwallowsErrors() {
        service.handle("not-a-json");
        verify(searchService, never()).indexResume(any(), any(), any());
    }
}
