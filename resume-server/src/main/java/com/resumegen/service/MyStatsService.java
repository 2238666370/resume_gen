package com.resumegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 我的数据后台（R9）：观阅 PV/UV、社区互动、分享访问量，强制 user_id 隔离。
 */
@Service
public class MyStatsService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ResumeMapper resumeMapper;
    private final AccessLogMapper accessLogMapper;
    private final ResumeShareMapper shareMapper;
    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final SysUserMapper userMapper;
    private final MyStatsMapper myStatsMapper;

    public MyStatsService(ResumeMapper resumeMapper, AccessLogMapper accessLogMapper,
                          ResumeShareMapper shareMapper, CommunityPostMapper postMapper,
                          CommunityCommentMapper commentMapper, SysUserMapper userMapper,
                          MyStatsMapper myStatsMapper) {
        this.resumeMapper = resumeMapper;
        this.accessLogMapper = accessLogMapper;
        this.shareMapper = shareMapper;
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.myStatsMapper = myStatsMapper;
    }

    /** 概览：我的简历数、总观阅、总互动、分享访问量。 */
    public MyStatsOverviewVO overview(Long userId) {
        List<Long> ids = myResumeIds(userId);
        MyStatsOverviewVO vo = new MyStatsOverviewVO();
        vo.setResumeCount(ids.size());
        if (ids.isEmpty()) {
            return vo;
        }
        vo.setPv(accessLogMapper.countPvByResumes(ids));
        vo.setUv(accessLogMapper.countUvByResumes(ids));
        vo.setLikeCount(postMapper.sumLikeByResumes(ids));
        vo.setCollectCount(postMapper.sumCollectByResumes(ids));
        vo.setCommentCount(postMapper.sumCommentByResumes(ids));
        vo.setShareViews(shareMapper.sumViewCountByUser(userId));
        return vo;
    }

    /** 按简历维度统计列表。 */
    public List<MyResumeStatVO> resumes(Long userId) {
        return myStatsMapper.listResumeStats(userId);
    }

    /** 单简历观阅趋势（校验归属）。 */
    public List<TrendPointVO> trend(Long userId, String resumeId, String granularity,
                                    LocalDateTime from, LocalDateTime to) {
        Resume r = ownedResume(userId, resumeId);
        return accessLogMapper.trendByResume(r.getId(), bucketFormat(granularity), from, to);
    }

    /** 单简历收到的社区评论（校验归属）。 */
    public List<CommentVO> comments(Long userId, String resumeId) {
        Resume r = ownedResume(userId, resumeId);
        List<Long> postIds = postMapper.selectList(new LambdaQueryWrapper<CommunityPost>()
                        .eq(CommunityPost::getResumeId, r.getId()))
                .stream().map(CommunityPost::getId).collect(Collectors.toList());
        if (postIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<CommunityComment> list = commentMapper.selectList(new LambdaQueryWrapper<CommunityComment>()
                .in(CommunityComment::getPostId, postIds)
                .eq(CommunityComment::getStatus, 1)
                .orderByDesc(CommunityComment::getId));
        Map<Long, SysUser> authors = userMap(list.stream()
                .map(CommunityComment::getUserId).distinct().collect(Collectors.toList()));
        List<CommentVO> out = new ArrayList<>();
        for (CommunityComment c : list) {
            out.add(toCommentVO(c, authors.get(c.getUserId())));
        }
        return out;
    }

    // ---------- 内部工具 ----------

    private List<Long> myResumeIds(Long userId) {
        return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, userId))
                .stream().map(Resume::getId).collect(Collectors.toList());
    }

    private Resume ownedResume(Long userId, String resumeId) {
        long id;
        try {
            id = Long.parseLong(resumeId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Resume r = resumeMapper.selectById(id);
        if (r == null || !userId.equals(r.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return r;
    }

    private Map<Long, SysUser> userMap(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
    }

    private CommentVO toCommentVO(CommunityComment c, SysUser author) {
        CommentVO vo = new CommentVO();
        vo.setId(String.valueOf(c.getId()));
        vo.setPostId(String.valueOf(c.getPostId()));
        vo.setUserId(String.valueOf(c.getUserId()));
        vo.setUserNickname(nickname(author));
        vo.setUserAvatar(author == null ? null : author.getAvatar());
        vo.setParentId(c.getParentId() == null ? null : String.valueOf(c.getParentId()));
        vo.setContent(c.getContent());
        vo.setCreatedAt(c.getCreatedAt() == null ? null : c.getCreatedAt().format(FMT));
        return vo;
    }

    private String nickname(SysUser u) {
        if (u == null) {
            return "";
        }
        return u.getNickname() != null && !u.getNickname().isBlank() ? u.getNickname() : u.getUsername();
    }

    private String bucketFormat(String granularity) {
        if ("minute".equalsIgnoreCase(granularity)) {
            return "%Y-%m-%d %H:%i:00";
        }
        if ("day".equalsIgnoreCase(granularity)) {
            return "%Y-%m-%d 00:00:00";
        }
        return "%Y-%m-%d %H:00:00"; // 默认 hour
    }
}
