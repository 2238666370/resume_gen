package com.resumegen.service;

import com.resumegen.common.BusinessException;
import com.resumegen.dto.CommentVO;
import com.resumegen.dto.MyResumeStatVO;
import com.resumegen.dto.MyStatsOverviewVO;
import com.resumegen.dto.TrendPointVO;
import com.resumegen.entity.CommunityComment;
import com.resumegen.entity.CommunityPost;
import com.resumegen.entity.Resume;
import com.resumegen.entity.SysUser;
import com.resumegen.mapper.AccessLogMapper;
import com.resumegen.mapper.CommunityCommentMapper;
import com.resumegen.mapper.CommunityPostMapper;
import com.resumegen.mapper.MyStatsMapper;
import com.resumegen.mapper.ResumeMapper;
import com.resumegen.mapper.ResumeShareMapper;
import com.resumegen.mapper.SysUserMapper;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyStatsServiceTest {

    @Mock private ResumeMapper resumeMapper;
    @Mock private AccessLogMapper accessLogMapper;
    @Mock private ResumeShareMapper shareMapper;
    @Mock private CommunityPostMapper postMapper;
    @Mock private CommunityCommentMapper commentMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private MyStatsMapper myStatsMapper;

    private MyStatsService service;

    @BeforeEach
    void setUp() {
        service = new MyStatsService(resumeMapper, accessLogMapper, shareMapper,
                postMapper, commentMapper, userMapper, myStatsMapper);
    }

    @Test
    void overviewEmptyResumesReturnsZeroWithoutAggregating() {
        when(resumeMapper.selectList(any())).thenReturn(List.of());

        MyStatsOverviewVO vo = service.overview(1L);

        assertThat(vo.getResumeCount()).isZero();
        assertThat(vo.getPv()).isZero();
        verify(accessLogMapper, never()).countPvByResumes(anyList());
    }

    @Test
    void overviewAggregatesAcrossMyResumes() {
        Resume r = new Resume();
        r.setId(9L);
        r.setUserId(1L);
        when(resumeMapper.selectList(any())).thenReturn(List.of(r));
        when(accessLogMapper.countPvByResumes(anyList())).thenReturn(12L);
        when(accessLogMapper.countUvByResumes(anyList())).thenReturn(5L);
        when(postMapper.sumLikeByResumes(anyList())).thenReturn(3L);
        when(shareMapper.sumViewCountByUser(1L)).thenReturn(7L);

        MyStatsOverviewVO vo = service.overview(1L);

        assertThat(vo.getResumeCount()).isEqualTo(1);
        assertThat(vo.getPv()).isEqualTo(12);
        assertThat(vo.getUv()).isEqualTo(5);
        assertThat(vo.getLikeCount()).isEqualTo(3);
        assertThat(vo.getShareViews()).isEqualTo(7);
    }

    @Test
    void resumesDelegatesToMapper() {
        MyResumeStatVO item = new MyResumeStatVO();
        item.setId("9");
        item.setPv(10);
        when(myStatsMapper.listResumeStats(1L)).thenReturn(List.of(item));

        assertThat(service.resumes(1L)).hasSize(1);
    }

    @Test
    void trendCrossUserReturns404() {
        when(resumeMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.trend(1L, "9", "hour",
                LocalDateTime.now().minusHours(1), LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void trendOwnedReturnsPoints() {
        Resume r = new Resume();
        r.setId(9L);
        r.setUserId(1L);
        when(resumeMapper.selectById(9L)).thenReturn(r);
        TrendPointVO p = new TrendPointVO();
        p.setTime("2026-10-01 10:00:00");
        p.setPv(3);
        when(accessLogMapper.trendByResume(eq(9L), anyString(), any(), any())).thenReturn(List.of(p));

        List<TrendPointVO> out = service.trend(1L, "9", "hour",
                LocalDateTime.now().minusHours(1), LocalDateTime.now());

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getPv()).isEqualTo(3);
    }

    @Test
    void commentsCrossUserReturns404() {
        when(resumeMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.comments(1L, "9"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void commentsAggregatesAcrossPosts() {
        Resume r = new Resume();
        r.setId(9L);
        r.setUserId(1L);
        when(resumeMapper.selectById(9L)).thenReturn(r);

        CommunityPost post = new CommunityPost();
        post.setId(100L);
        post.setResumeId(9L);
        when(postMapper.selectList(any())).thenReturn(List.of(post));

        CommunityComment c = new CommunityComment();
        c.setId(1L);
        c.setPostId(100L);
        c.setUserId(2L);
        c.setContent("不错");
        c.setStatus(1);
        c.setCreatedAt(LocalDateTime.of(2026, 10, 1, 10, 0));
        when(commentMapper.selectList(any())).thenReturn(List.of(c));

        SysUser author = new SysUser();
        author.setId(2L);
        author.setNickname("评论者");
        when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(author));

        List<CommentVO> out = service.comments(1L, "9");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getContent()).isEqualTo("不错");
        assertThat(out.get(0).getUserNickname()).isEqualTo("评论者");
    }
}
