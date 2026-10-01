package com.resumegen.service;

import com.resumegen.cache.PostCounterStore;
import com.resumegen.common.BusinessException;
import com.resumegen.config.ResumeProperties;
import com.resumegen.dto.CommentRequest;
import com.resumegen.dto.PostPublishRequest;
import com.resumegen.entity.CommunityComment;
import com.resumegen.entity.CommunityLike;
import com.resumegen.entity.CommunityPost;
import com.resumegen.mapper.CommunityCollectMapper;
import com.resumegen.mapper.CommunityCommentMapper;
import com.resumegen.mapper.CommunityLikeMapper;
import com.resumegen.mapper.CommunityPostMapper;
import com.resumegen.mapper.CommunityReportMapper;
import com.resumegen.mapper.SysUserMapper;
import com.resumegen.rule.AuditRuleEngine;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private CommunityPostMapper postMapper;
    @Mock
    private CommunityLikeMapper likeMapper;
    @Mock
    private CommunityCollectMapper collectMapper;
    @Mock
    private CommunityCommentMapper commentMapper;
    @Mock
    private CommunityReportMapper reportMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ResumeService resumeService;
    @Mock
    private PostCounterStore counterStore;
    @Mock
    private SearchService searchService;
    @Mock
    private SearchIndexSyncService searchIndexSync;

    private CommunityService service;

    @BeforeEach
    void setUp() {
        ResumeProperties props = new ResumeProperties();
        props.getCommunity().setSensitiveWords(List.of("广告"));
        props.getCommunity().setReportReviewThreshold(2);
        props.getCommunity().setLikeReviewThreshold(1000);
        props.getCommunity().setViewReviewThreshold(10000);
        AuditRuleEngine engine = new AuditRuleEngine(props);
        service = new CommunityService(postMapper, likeMapper, collectMapper,
                commentMapper, reportMapper, userMapper, resumeService, engine, props, counterStore,
                searchService, searchIndexSync);
    }

    private CommunityPost onlinePost(Long id) {
        CommunityPost p = new CommunityPost();
        p.setId(id);
        p.setUserId(1L);
        p.setResumeId(1L);
        p.setAuditStatus(CommunityPost.ST_ONLINE);
        p.setReportCount(0);
        p.setLikeCount(0L);
        p.setViewCount(0L);
        return p;
    }

    @Test
    void publishCleanGoesOnline() {
        when(resumeService.get(1L, "1")).thenReturn(null);

        PostPublishRequest req = new PostPublishRequest();
        req.setResumeId(1L);
        req.setTitle("前端工程师求职");
        req.setTags(List.of("前端"));

        service.publish(1L, req);

        ArgumentCaptor<CommunityPost> captor = ArgumentCaptor.forClass(CommunityPost.class);
        verify(postMapper).insert(captor.capture());
        assertThat(captor.getValue().getAuditStatus()).isEqualTo(CommunityPost.ST_ONLINE);
        assertThat(captor.getValue().getAuditReason()).isNull();
    }

    @Test
    void publishWithSensitiveWordRejected() {
        when(resumeService.get(1L, "1")).thenReturn(null);

        PostPublishRequest req = new PostPublishRequest();
        req.setResumeId(1L);
        req.setTitle("低价代发广告");

        service.publish(1L, req);

        ArgumentCaptor<CommunityPost> captor = ArgumentCaptor.forClass(CommunityPost.class);
        verify(postMapper).insert(captor.capture());
        assertThat(captor.getValue().getAuditStatus()).isEqualTo(CommunityPost.ST_REJECTED);
        assertThat(captor.getValue().getAuditReason()).contains("广告");
    }

    @Test
    void likeInsertsWhenNotLiked() {
        when(postMapper.selectById(10L)).thenReturn(onlinePost(10L));
        when(likeMapper.selectOne(any())).thenReturn(null);
        when(likeMapper.selectCount(any())).thenReturn(1L);

        service.like(2L, 10L);

        verify(likeMapper).insert(any(CommunityLike.class));
        verify(postMapper).incrLike(10L);
    }

    @Test
    void likeRemovesWhenAlreadyLiked() {
        when(postMapper.selectById(10L)).thenReturn(onlinePost(10L));
        CommunityLike existing = new CommunityLike();
        existing.setId(99L);
        when(likeMapper.selectOne(any())).thenReturn(existing);
        when(likeMapper.selectCount(any())).thenReturn(0L);

        service.like(2L, 10L);

        verify(likeMapper).deleteById(99L);
        verify(postMapper).decrLike(10L);
    }

    @Test
    void reportTriggersReviewWhenThresholdReached() {
        CommunityPost p = onlinePost(10L);
        p.setReportCount(2);
        when(postMapper.selectById(10L)).thenReturn(p);

        com.resumegen.dto.ReportRequest req = new com.resumegen.dto.ReportRequest();
        req.setReason("违规");
        service.report(2L, 10L, req);

        ArgumentCaptor<CommunityPost> captor = ArgumentCaptor.forClass(CommunityPost.class);
        verify(postMapper).updateById(captor.capture());
        assertThat(captor.getValue().getAuditStatus()).isEqualTo(CommunityPost.ST_REVIEW);
    }

    @Test
    void commentInsertsAndIncrements() {
        when(postMapper.selectById(10L)).thenReturn(onlinePost(10L));

        CommentRequest req = new CommentRequest();
        req.setContent("写得不错");
        service.comment(2L, 10L, req);

        verify(commentMapper).insert(any(CommunityComment.class));
        verify(postMapper).incrComment(10L);
    }

    @Test
    void deleteCommentOnlyOwner() {
        CommunityComment c = new CommunityComment();
        c.setId(7L);
        c.setPostId(10L);
        c.setUserId(1L);
        c.setStatus(1);
        when(commentMapper.selectById(7L)).thenReturn(c);

        assertThatThrownBy(() -> service.deleteComment(2L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
    }
}