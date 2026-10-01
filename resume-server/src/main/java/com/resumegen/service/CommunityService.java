package com.resumegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.common.PageResult;
import com.resumegen.config.ResumeProperties;
import com.resumegen.dto.AdminPostItemVO;
import com.resumegen.dto.AdminReportItemVO;
import com.resumegen.dto.AuditRequest;
import com.resumegen.dto.CommentRequest;
import com.resumegen.dto.CommentVO;
import com.resumegen.dto.InteractionVO;
import com.resumegen.dto.MyCommunityVO;
import com.resumegen.dto.MyPostVO;
import com.resumegen.dto.PostDetailVO;
import com.resumegen.dto.PostPublishRequest;
import com.resumegen.dto.PostVO;
import com.resumegen.dto.ReportRequest;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.entity.CommunityCollect;
import com.resumegen.entity.CommunityComment;
import com.resumegen.entity.CommunityLike;
import com.resumegen.entity.CommunityPost;
import com.resumegen.entity.CommunityReport;
import com.resumegen.entity.SysUser;
import com.resumegen.mapper.CommunityCollectMapper;
import com.resumegen.mapper.CommunityCommentMapper;
import com.resumegen.mapper.CommunityLikeMapper;
import com.resumegen.mapper.CommunityPostMapper;
import com.resumegen.mapper.CommunityReportMapper;
import com.resumegen.mapper.SysUserMapper;
import com.resumegen.rule.AuditRuleEngine;
import com.resumegen.cache.PostCounterStore;
import com.resumegen.cache.PostCounterStore.Field;
import com.resumegen.search.SearchHits;
import com.resumegen.search.SearchIndexSyncService;
import com.resumegen.search.SearchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 简历社区业务：先审后发、匿名浏览、点赞/收藏/评论/举报、高热度复审、治理。
 */
@Service
public class CommunityService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CommunityPostMapper postMapper;
    private final CommunityLikeMapper likeMapper;
    private final CommunityCollectMapper collectMapper;
    private final CommunityCommentMapper commentMapper;
    private final CommunityReportMapper reportMapper;
    private final SysUserMapper userMapper;
    private final ResumeService resumeService;
    private final AuditRuleEngine ruleEngine;
    private final ResumeProperties props;
    private final PostCounterStore counterStore;
    private final SearchService searchService;
    private final SearchIndexSyncService searchIndexSync;

    public CommunityService(CommunityPostMapper postMapper, CommunityLikeMapper likeMapper,
                            CommunityCollectMapper collectMapper, CommunityCommentMapper commentMapper,
                            CommunityReportMapper reportMapper, SysUserMapper userMapper,
                            ResumeService resumeService, AuditRuleEngine ruleEngine, ResumeProperties props,
                            PostCounterStore counterStore, SearchService searchService,
                            SearchIndexSyncService searchIndexSync) {
        this.postMapper = postMapper;
        this.likeMapper = likeMapper;
        this.collectMapper = collectMapper;
        this.commentMapper = commentMapper;
        this.reportMapper = reportMapper;
        this.userMapper = userMapper;
        this.resumeService = resumeService;
        this.ruleEngine = ruleEngine;
        this.props = props;
        this.counterStore = counterStore;
        this.searchService = searchService;
        this.searchIndexSync = searchIndexSync;
    }

    // ---------- 发布（先审后发） ----------

    @Transactional
    public MyPostVO publish(Long userId, PostPublishRequest req) {
        // 校验简历归属（不存在/非本人则 NOT_FOUND）
        resumeService.get(userId, String.valueOf(req.getResumeId()));

        CommunityPost p = new CommunityPost();
        p.setUserId(userId);
        p.setResumeId(req.getResumeId());
        p.setTitle(req.getTitle().trim());
        p.setSummary(req.getSummary());
        p.setCoverUrl(req.getCoverUrl());
        p.setTags(joinTags(req.getTags()));
        p.setLikeCount(0L);
        p.setCollectCount(0L);
        p.setCommentCount(0L);
        p.setViewCount(0L);
        p.setReportCount(0);

        AuditRuleEngine.AuditResult result = ruleEngine.audit(p.getTitle(), p.getSummary(), req.getTags());
        if (result.passed()) {
            p.setAuditStatus(CommunityPost.ST_ONLINE);
        } else {
            p.setAuditStatus(CommunityPost.ST_REJECTED);
            p.setAuditReason(result.reason());
        }
        postMapper.insert(p);
        searchIndexSync.syncPost(p.getId());
        return toMyPostVO(p, author(userId));
    }

    // ---------- 信息流 / 详情（匿名可访问，仅上线内容） ----------

    public PageResult<PostVO> listPublic(long page, long size, String tag, String keyword, String sort) {
        // ES 检索优先（active 且有关键词），失败/未启用时降级 MySQL LIKE
        if (searchService.active() && keyword != null && !keyword.isBlank()) {
            SearchHits<Long> hits = searchService.searchPosts(keyword, tag, sort, page, size);
            if (hits != null) {
                List<CommunityPost> posts = hits.getIds().isEmpty() ? new ArrayList<>()
                        : postMapper.selectBatchIds(hits.getIds());
                Map<Long, SysUser> authors = authorsOf(posts);
                Map<Long, CommunityPost> byId = posts.stream()
                        .collect(Collectors.toMap(CommunityPost::getId, x -> x, (a, b) -> a));
                List<PostVO> records = new ArrayList<>();
                for (Long id : hits.getIds()) {
                    CommunityPost p = byId.get(id);
                    if (p != null) {
                        records.add(toPostVO(p, authors.get(p.getUserId())));
                    }
                }
                return new PageResult<>(records, hits.getTotal(), page, size);
            }
        }
        LambdaQueryWrapper<CommunityPost> qw = new LambdaQueryWrapper<>();
        qw.eq(CommunityPost::getAuditStatus, CommunityPost.ST_ONLINE);
        if (tag != null && !tag.isBlank()) {
            qw.apply("FIND_IN_SET({0}, tags)", tag.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(CommunityPost::getTitle, kw)
                    .or().like(CommunityPost::getSummary, kw)
                    .or().like(CommunityPost::getTags, kw));
        }
        if ("hot".equals(sort)) {
            qw.orderByDesc(CommunityPost::getLikeCount).orderByDesc(CommunityPost::getId);
        } else {
            qw.orderByDesc(CommunityPost::getId);
        }
        Page<CommunityPost> p = postMapper.selectPage(new Page<>(page, size), qw);
        Map<Long, SysUser> authors = authorsOf(p.getRecords());
        List<PostVO> records = p.getRecords().stream()
                .map(x -> toPostVO(x, authors.get(x.getUserId())))
                .collect(Collectors.toList());
        return new PageResult<>(records, p.getTotal(), page, size);
    }

    public PostDetailVO detailPublic(Long postId) {
        CommunityPost p = requirePost(postId);
        requireOnline(p);
        postMapper.incrView(postId);
        maybeTriggerReview(postId);

        ResumeDetailVO resume = resumeService.get(p.getUserId(), String.valueOf(p.getResumeId()));
        // 脱敏联系方式
        if (resume.getPersonal() != null) {
            resume.getPersonal().setEmail(null);
            resume.getPersonal().setPhone(null);
        }

        PostDetailVO vo = new PostDetailVO();
        copyPost(vo, p, author(p.getUserId()));
        vo.setViewCount(nvl(p.getViewCount()) + 1);
        vo.setResume(resume);
        return vo;
    }

    // ---------- 互动（需登录） ----------

    public InteractionVO interaction(Long userId, Long postId) {
        CommunityPost p = postMapper.selectById(postId);
        Map<Field, Long> c = p == null ? null : counters(p);
        long likeCount = c == null ? 0 : c.getOrDefault(Field.LIKE, 0L);
        long collectCount = c == null ? 0 : c.getOrDefault(Field.COLLECT, 0L);

        InteractionVO vo = new InteractionVO();
        vo.setLiked(existsLike(userId, postId));
        vo.setCollected(existsCollect(userId, postId));
        vo.setLikeCount(likeCount);
        vo.setCollectCount(collectCount);
        return vo;
    }

    @Transactional
    public InteractionVO like(Long userId, Long postId) {
        CommunityPost p = requirePost(postId);
        requireOnline(p);

        CommunityLike existing = likeMapper.selectOne(new LambdaQueryWrapper<CommunityLike>()
                .eq(CommunityLike::getPostId, postId)
                .eq(CommunityLike::getUserId, userId));
        if (existing == null) {
            CommunityLike l = new CommunityLike();
            l.setPostId(postId);
            l.setUserId(userId);
            likeMapper.insert(l);
            postMapper.incrLike(postId);
            counterStore.incr(postId, Field.LIKE, 1);
        } else {
            likeMapper.deleteById(existing.getId());
            postMapper.decrLike(postId);
            counterStore.incr(postId, Field.LIKE, -1);
        }
        maybeTriggerReview(postId);
        return interaction(userId, postId);
    }

    @Transactional
    public InteractionVO collect(Long userId, Long postId) {
        CommunityPost p = requirePost(postId);
        requireOnline(p);

        CommunityCollect existing = collectMapper.selectOne(new LambdaQueryWrapper<CommunityCollect>()
                .eq(CommunityCollect::getPostId, postId)
                .eq(CommunityCollect::getUserId, userId));
        if (existing == null) {
            CommunityCollect c = new CommunityCollect();
            c.setPostId(postId);
            c.setUserId(userId);
            collectMapper.insert(c);
            postMapper.incrCollect(postId);
            counterStore.incr(postId, Field.COLLECT, 1);
        } else {
            collectMapper.deleteById(existing.getId());
            postMapper.decrCollect(postId);
            counterStore.incr(postId, Field.COLLECT, -1);
        }
        return interaction(userId, postId);
    }

    // ---------- 评论（楼中楼） ----------

    public List<CommentVO> listComments(Long postId) {
        List<CommunityComment> list = commentMapper.selectList(new LambdaQueryWrapper<CommunityComment>()
                .eq(CommunityComment::getPostId, postId)
                .eq(CommunityComment::getStatus, 1)
                .orderByAsc(CommunityComment::getId));
        Map<Long, SysUser> authors = userMap(list.stream()
                .map(CommunityComment::getUserId).distinct().collect(Collectors.toList()));

        Map<Long, CommentVO> byId = list.stream()
                .collect(Collectors.toMap(CommunityComment::getId,
                        c -> toCommentVO(c, authors.get(c.getUserId())), (a, b) -> a));

        List<CommentVO> roots = new ArrayList<>();
        for (CommunityComment c : list) {
            CommentVO vo = byId.get(c.getId());
            if (c.getParentId() == null) {
                roots.add(vo);
            } else {
                CommentVO parent = byId.get(c.getParentId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    roots.add(vo);
                }
            }
        }
        return roots;
    }

    @Transactional
    public CommentVO comment(Long userId, Long postId, CommentRequest req) {
        CommunityPost p = requirePost(postId);
        requireOnline(p);
        if (req.getParentId() != null) {
            CommunityComment parent = commentMapper.selectById(req.getParentId());
            if (parent == null || !Objects.equals(parent.getPostId(), postId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "被回复的评论不存在");
            }
        }
        CommunityComment c = new CommunityComment();
        c.setPostId(postId);
        c.setUserId(userId);
        c.setParentId(req.getParentId());
        c.setContent(req.getContent().trim());
        c.setStatus(1);
        commentMapper.insert(c);
        postMapper.incrComment(postId);
        counterStore.incr(postId, Field.COMMENT, 1);
        return toCommentVO(c, author(userId));
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        CommunityComment c = commentMapper.selectById(commentId);
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!Objects.equals(c.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "只能删除自己的评论");
        }
        c.setStatus(0);
        commentMapper.updateById(c);
        postMapper.decrComment(c.getPostId());
        counterStore.incr(c.getPostId(), Field.COMMENT, -1);
    }

    // ---------- 举报 ----------

    @Transactional
    public void report(Long userId, Long postId, ReportRequest req) {
        CommunityPost p = requirePost(postId);
        requireOnline(p);
        CommunityReport r = new CommunityReport();
        r.setPostId(postId);
        r.setUserId(userId);
        r.setReason(req.getReason());
        r.setStatus(CommunityReport.ST_PENDING);
        reportMapper.insert(r);
        postMapper.incrReport(postId);
        counterStore.incr(postId, Field.REPORT, 1);
        maybeTriggerReview(postId);
    }

    // ---------- 我的 ----------

    public MyCommunityVO my(Long userId) {
        List<CommunityPost> myPosts = postMapper.selectList(new LambdaQueryWrapper<CommunityPost>()
                .eq(CommunityPost::getUserId, userId)
                .orderByDesc(CommunityPost::getId));

        List<Long> likedIds = likeMapper.selectList(new LambdaQueryWrapper<CommunityLike>()
                        .eq(CommunityLike::getUserId, userId))
                .stream().map(CommunityLike::getPostId).collect(Collectors.toList());
        List<Long> collectIds = collectMapper.selectList(new LambdaQueryWrapper<CommunityCollect>()
                        .eq(CommunityCollect::getUserId, userId))
                .stream().map(CommunityCollect::getPostId).collect(Collectors.toList());

        MyCommunityVO vo = new MyCommunityVO();
        vo.setPosts(myPosts.stream().map(p -> toMyPostVO(p, author(p.getUserId()))).collect(Collectors.toList()));
        vo.setLikes(postsOf(likedIds));
        vo.setCollects(postsOf(collectIds));
        return vo;
    }

    // ---------- 治理（管理端） ----------

    public PageResult<AdminPostItemVO> adminList(long page, long size, Integer status) {
        LambdaQueryWrapper<CommunityPost> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(CommunityPost::getAuditStatus, status);
        }
        qw.orderByDesc(CommunityPost::getId);
        Page<CommunityPost> p = postMapper.selectPage(new Page<>(page, size), qw);
        Map<Long, SysUser> authors = authorsOf(p.getRecords());
        List<AdminPostItemVO> records = p.getRecords().stream()
                .map(x -> toAdminPostVO(x, authors.get(x.getUserId())))
                .collect(Collectors.toList());
        return new PageResult<>(records, p.getTotal(), page, size);
    }

    @Transactional
    public void adminAudit(Long postId, AuditRequest req) {
        CommunityPost p = requirePost(postId);
        if (req.getStatus() == null || (req.getStatus() != CommunityPost.ST_ONLINE
                && req.getStatus() != CommunityPost.ST_REJECTED
                && req.getStatus() != CommunityPost.ST_OFFLINE)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "审核状态不合法");
        }
        p.setAuditStatus(req.getStatus());
        p.setAuditReason(req.getReason());
        postMapper.updateById(p);
        searchIndexSync.syncPost(p.getId());
    }

    @Transactional
    public void adminDelete(Long postId) {
        requirePost(postId);
        postMapper.deleteById(postId);
        likeMapper.delete(new LambdaQueryWrapper<CommunityLike>().eq(CommunityLike::getPostId, postId));
        collectMapper.delete(new LambdaQueryWrapper<CommunityCollect>().eq(CommunityCollect::getPostId, postId));
        commentMapper.delete(new LambdaQueryWrapper<CommunityComment>().eq(CommunityComment::getPostId, postId));
        reportMapper.delete(new LambdaQueryWrapper<CommunityReport>().eq(CommunityReport::getPostId, postId));
        counterStore.delete(postId);
        searchIndexSync.syncDeletePost(postId);
    }

    public PageResult<AdminReportItemVO> adminReports(long page, long size) {
        Page<CommunityReport> p = reportMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<CommunityReport>()
                        .eq(CommunityReport::getStatus, CommunityReport.ST_PENDING)
                        .orderByDesc(CommunityReport::getId));
        Map<Long, SysUser> reporters = userMap(p.getRecords().stream()
                .map(CommunityReport::getUserId).distinct().collect(Collectors.toList()));
        Map<Long, CommunityPost> posts = postMap(p.getRecords().stream()
                .map(CommunityReport::getPostId).distinct().collect(Collectors.toList()));

        List<AdminReportItemVO> records = p.getRecords().stream().map(r -> {
            AdminReportItemVO vo = new AdminReportItemVO();
            vo.setId(String.valueOf(r.getId()));
            vo.setPostId(String.valueOf(r.getPostId()));
            CommunityPost post = posts.get(r.getPostId());
            vo.setPostTitle(post == null ? "（已删除）" : post.getTitle());
            vo.setReporterId(String.valueOf(r.getUserId()));
            SysUser u = reporters.get(r.getUserId());
            vo.setReporterNickname(nickname(u));
            vo.setReason(r.getReason());
            vo.setStatus(r.getStatus() == null ? 0 : r.getStatus());
            vo.setCreatedAt(r.getCreatedAt() == null ? null : r.getCreatedAt().format(FMT));
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(records, p.getTotal(), page, size);
    }

    @Transactional
    public void adminResolveReport(Long reportId) {
        CommunityReport r = reportMapper.selectById(reportId);
        if (r == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        r.setStatus(CommunityReport.ST_RESOLVED);
        reportMapper.updateById(r);
    }

    // ---------- 内部工具 ----------

    private CommunityPost requirePost(Long postId) {
        CommunityPost p = postMapper.selectById(postId);
        if (p == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "帖子不存在");
        }
        return p;
    }

    private void requireOnline(CommunityPost p) {
        if (p.getAuditStatus() == null || p.getAuditStatus() != CommunityPost.ST_ONLINE) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "帖子不存在或已下线");
        }
    }

    private void maybeTriggerReview(Long postId) {
        CommunityPost p = postMapper.selectById(postId);
        if (p == null || p.getAuditStatus() == null || p.getAuditStatus() != CommunityPost.ST_ONLINE) {
            return;
        }
        ResumeProperties.Community c = props.getCommunity();
        boolean hot = (p.getReportCount() != null && p.getReportCount() >= c.getReportReviewThreshold())
                || (p.getLikeCount() != null && p.getLikeCount() >= c.getLikeReviewThreshold())
                || (p.getViewCount() != null && p.getViewCount() >= c.getViewReviewThreshold());
        if (hot) {
            CommunityPost u = new CommunityPost();
            u.setId(postId);
            u.setAuditStatus(CommunityPost.ST_REVIEW);
            u.setAuditReason("热度异常，转人工复审");
            postMapper.updateById(u);
            searchIndexSync.syncPost(postId);
        }
    }

    private boolean existsLike(Long userId, Long postId) {
        Long c = likeMapper.selectCount(new LambdaQueryWrapper<CommunityLike>()
                .eq(CommunityLike::getPostId, postId).eq(CommunityLike::getUserId, userId));
        return c != null && c > 0;
    }

    private boolean existsCollect(Long userId, Long postId) {
        Long c = collectMapper.selectCount(new LambdaQueryWrapper<CommunityCollect>()
                .eq(CommunityCollect::getPostId, postId).eq(CommunityCollect::getUserId, userId));
        return c != null && c > 0;
    }

    private List<PostVO> postsOf(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<CommunityPost> posts = postMapper.selectList(new LambdaQueryWrapper<CommunityPost>()
                .in(CommunityPost::getId, ids)
                .eq(CommunityPost::getAuditStatus, CommunityPost.ST_ONLINE)
                .orderByDesc(CommunityPost::getId));
        Map<Long, SysUser> authors = authorsOf(posts);
        return posts.stream().map(p -> toPostVO(p, authors.get(p.getUserId()))).collect(Collectors.toList());
    }

    private Map<Long, SysUser> authorsOf(List<CommunityPost> posts) {
        List<Long> ids = posts.stream().map(CommunityPost::getUserId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        return userMap(ids);
    }

    private Map<Long, SysUser> userMap(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
    }

    private Map<Long, CommunityPost> postMap(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return postMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CommunityPost::getId, p -> p, (a, b) -> a));
    }

    private SysUser author(Long id) {
        return id == null ? null : userMapper.selectById(id);
    }

    private String nickname(SysUser u) {
        if (u == null) {
            return "";
        }
        return u.getNickname() != null && !u.getNickname().isBlank() ? u.getNickname() : u.getUsername();
    }

    private void copyPost(PostVO vo, CommunityPost p, SysUser author) {
        Map<Field, Long> c = counters(p);
        vo.setId(String.valueOf(p.getId()));
        vo.setTitle(p.getTitle());
        vo.setSummary(p.getSummary());
        vo.setTags(splitTags(p.getTags()));
        vo.setCoverUrl(p.getCoverUrl());
        vo.setAuthorId(author == null ? null : String.valueOf(author.getId()));
        vo.setAuthorNickname(nickname(author));
        vo.setAuthorAvatar(author == null ? null : author.getAvatar());
        vo.setLikeCount(c.getOrDefault(Field.LIKE, 0L));
        vo.setCollectCount(c.getOrDefault(Field.COLLECT, 0L));
        vo.setCommentCount(c.getOrDefault(Field.COMMENT, 0L));
        vo.setViewCount(nvl(p.getViewCount()));
        vo.setCreatedAt(p.getCreatedAt() == null ? null : p.getCreatedAt().format(FMT));
    }

    private PostVO toPostVO(CommunityPost p, SysUser author) {
        PostVO vo = new PostVO();
        copyPost(vo, p, author);
        return vo;
    }

    private MyPostVO toMyPostVO(CommunityPost p, SysUser author) {
        MyPostVO vo = new MyPostVO();
        copyPost(vo, p, author);
        vo.setAuditStatus(p.getAuditStatus() == null ? CommunityPost.ST_PENDING : p.getAuditStatus());
        vo.setAuditReason(p.getAuditReason());
        return vo;
    }

    private AdminPostItemVO toAdminPostVO(CommunityPost p, SysUser author) {
        AdminPostItemVO vo = new AdminPostItemVO();
        Map<Field, Long> c = counters(p);
        vo.setId(String.valueOf(p.getId()));
        vo.setTitle(p.getTitle());
        vo.setAuthorId(author == null ? null : String.valueOf(author.getId()));
        vo.setAuthorNickname(nickname(author));
        vo.setAuditStatus(p.getAuditStatus() == null ? CommunityPost.ST_PENDING : p.getAuditStatus());
        vo.setAuditReason(p.getAuditReason());
        vo.setLikeCount(c.getOrDefault(Field.LIKE, 0L));
        vo.setCollectCount(c.getOrDefault(Field.COLLECT, 0L));
        vo.setCommentCount(c.getOrDefault(Field.COMMENT, 0L));
        vo.setViewCount(nvl(p.getViewCount()));
        vo.setReportCount(c.getOrDefault(Field.REPORT, 0L).intValue());
        vo.setCreatedAt(p.getCreatedAt() == null ? null : p.getCreatedAt().format(FMT));
        return vo;
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

    private long nvl(Long v) {
        return v == null ? 0 : v;
    }

    /**
     * 读直读：优先 Redis 计数（写扩散读模型），缺失回退 DB 实体值并读穿透回填自愈。
     * 浏览数不走计数缓存（写时 incrView + 读取时 +1），保持原有语义。
     */
    private Map<Field, Long> counters(CommunityPost p) {
        Map<Field, Long> c = counterStore.getAll(p.getId());
        if (c == null || c.isEmpty()) {
            c = new EnumMap<>(Field.class);
            c.put(Field.LIKE, nvl(p.getLikeCount()));
            c.put(Field.COLLECT, nvl(p.getCollectCount()));
            c.put(Field.COMMENT, nvl(p.getCommentCount()));
            c.put(Field.REPORT, (long) (p.getReportCount() == null ? 0 : p.getReportCount()));
            counterStore.saveAll(p.getId(), c);
        }
        return c;
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String joined = tags.stream().filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(tags.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }
}