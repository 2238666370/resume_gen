package com.resumegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.common.PageResult;
import com.resumegen.dto.TemplateCreateRequest;
import com.resumegen.dto.TemplateSaveRequest;
import com.resumegen.dto.TemplateUpdateRequest;
import com.resumegen.dto.TemplateVO;
import com.resumegen.entity.Template;
import com.resumegen.mapper.TemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 简历模板业务：公开列表/详情、管理端 CRUD、用户自定义模板（R8-A1）、
 * 模板市场（R8-A3，发布/审核/搜索/一键用）。
 */
@Service
public class TemplateService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 状态位：0下架 1上架(online) 2待审(pending) 3草稿(draft)。 */
    public static final int STATUS_OFFLINE = 0;
    public static final int STATUS_ONLINE = 1;
    public static final int STATUS_PENDING = 2;
    public static final int STATUS_DRAFT = 3;

    private final TemplateMapper templateMapper;
    private final SchemaValidator schemaValidator;

    /** 公开模板列表本地缓存（R10-O6 多级缓存第一级，JDK 无依赖实现，模板变更时失效）。 */
    private static final long LIST_CACHE_TTL_MS = 60_000L;
    private volatile List<TemplateVO> cachedPublicList;
    private volatile long cachedPublicListAt;

    public TemplateService(TemplateMapper templateMapper, SchemaValidator schemaValidator) {
        this.templateMapper = templateMapper;
        this.schemaValidator = schemaValidator;
    }

    /** 公开：上架模板列表（官方在前，其次用户上架模板，按 sort_order 升序）。 */
    public List<TemplateVO> listPublic() {
        long now = System.currentTimeMillis();
        List<TemplateVO> cached = cachedPublicList;
        if (cached != null && now - cachedPublicListAt < LIST_CACHE_TTL_MS) {
            return cached;
        }
        synchronized (this) {
            if (cachedPublicList == null || now - cachedPublicListAt >= LIST_CACHE_TTL_MS) {
                cachedPublicList = templateMapper.selectList(
                                new LambdaQueryWrapper<Template>()
                                        .eq(Template::getStatus, STATUS_ONLINE)
                                        .orderByAsc(Template::getType)
                                        .orderByAsc(Template::getSortOrder)
                                        .orderByAsc(Template::getId))
                        .stream().map(this::toVO).collect(Collectors.toList());
                cachedPublicListAt = System.currentTimeMillis();
            }
            return cachedPublicList;
        }
    }

    private void evictPublicListCache() {
        cachedPublicList = null;
        cachedPublicListAt = 0;
    }

    /** 公开：上架模板详情。 */
    public TemplateVO getByCode(String code) {
        Template t = templateMapper.selectOne(
                new LambdaQueryWrapper<Template>()
                        .eq(Template::getCode, code)
                        .eq(Template::getStatus, STATUS_ONLINE));
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "模板不存在");
        }
        return toVO(t);
    }

    // ---------------- 管理端 ----------------

    /** 管理：全量列表（含下架/待审/草稿）。 */
    public PageResult<TemplateVO> adminList(long page, long size) {
        Page<Template> p = templateMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Template>()
                        .orderByAsc(Template::getType)
                        .orderByAsc(Template::getSortOrder)
                        .orderByAsc(Template::getId));
        List<TemplateVO> records = p.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(records, p.getTotal(), page, size);
    }

    /** 管理：新增模板。 */
    @Transactional
    public TemplateVO create(TemplateCreateRequest req) {
        Long c = templateMapper.selectCount(
                new LambdaQueryWrapper<Template>().eq(Template::getCode, req.getCode()));
        if (c != null && c > 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "模板编码已存在");
        }
        Template t = new Template();
        t.setCode(req.getCode());
        t.setName(req.getName());
        t.setType(req.getType() == null || req.getType().isBlank() ? "official" : req.getType());
        t.setCategory(req.getCategory());
        t.setThumbnail(req.getThumbnail());
        t.setSchema(req.getSchema() == null ? null : schemaValidator.validate(req.getSchema()));
        t.setVersion(0);
        t.setSchemaVersion(1);
        t.setUseCount(0L);
        t.setViewCount(0L);
        t.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        t.setStatus(STATUS_ONLINE);
        templateMapper.insert(t);
        evictPublicListCache();
        return toVO(t);
    }

    /** 管理：更新模板（含上下架，非空字段局部更新）。 */
    @Transactional
    public TemplateVO update(Long id, TemplateUpdateRequest req) {
        Template t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (req.getName() != null) {
            t.setName(req.getName());
        }
        if (req.getType() != null) {
            t.setType(req.getType());
        }
        if (req.getCategory() != null) {
            t.setCategory(req.getCategory());
        }
        if (req.getThumbnail() != null) {
            t.setThumbnail(req.getThumbnail());
        }
        if (req.getSchema() != null) {
            t.setSchema(schemaValidator.validate(req.getSchema()));
        }
        if (req.getSortOrder() != null) {
            t.setSortOrder(req.getSortOrder());
        }
        if (req.getStatus() != null) {
            t.setStatus(req.getStatus());
            if (req.getStatus() == STATUS_ONLINE) {
                t.setPublishedAt(LocalDateTime.now());
            }
        }
        if (req.getAuditReason() != null) {
            t.setAuditReason(req.getAuditReason());
        }
        templateMapper.updateById(t);
        evictPublicListCache();
        return toVO(t);
    }

    /** 管理：审核（通过→上架 / 拒绝→回退草稿）。 */
    @Transactional
    public TemplateVO audit(Long id, boolean approve, String reason) {
        Template t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (approve) {
            t.setStatus(STATUS_ONLINE);
            t.setPublishedAt(LocalDateTime.now());
            t.setAuditReason(null);
        } else {
            t.setStatus(STATUS_DRAFT);
            t.setAuditReason(reason == null ? "未通过审核" : reason);
        }
        templateMapper.updateById(t);
        evictPublicListCache();
        return toVO(t);
    }

    /** 管理：删除模板。 */
    @Transactional
    public void delete(Long id) {
        Template t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        templateMapper.deleteById(id);
        evictPublicListCache();
    }

    // ---------------- 用户自定义模板（R8-A1） ----------------

    /** 创建用户自定义模板（type=user，草稿态）。 */
    @Transactional
    public TemplateVO createUserTemplate(Long userId, TemplateSaveRequest req) {
        String schema = schemaValidator.validate(req.getSchema());
        Template t = new Template();
        t.setCode("u_" + userId + "_" + System.currentTimeMillis());
        t.setName(req.getName());
        t.setType("user");
        t.setCategory(req.getCategory());
        t.setSchema(schema);
        t.setOwnerUserId(userId);
        t.setVersion(0);
        t.setSchemaVersion(1);
        t.setUseCount(0L);
        t.setViewCount(0L);
        t.setSortOrder(0);
        t.setStatus(STATUS_DRAFT);
        templateMapper.insert(t);
        return toVO(t);
    }

    /** 更新用户自定义模板（归属校验 + 乐观锁）。 */
    @Transactional
    public TemplateVO updateUserTemplate(Long userId, Long id, TemplateSaveRequest req) {
        String schema = schemaValidator.validate(req.getSchema());
        Template t = owned(userId, id);
        int expected = t.getVersion() == null ? 0 : t.getVersion();
        int rows = templateMapper.update(null, new LambdaUpdateWrapper<Template>()
                .eq(Template::getId, id)
                .eq(Template::getOwnerUserId, userId)
                .eq(Template::getVersion, expected)
                .set(Template::getName, req.getName())
                .set(Template::getCategory, req.getCategory())
                .set(Template::getSchema, schema)
                .set(Template::getVersion, expected + 1));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "模板已被他人修改，请刷新后重试");
        }
        return toVO(templateMapper.selectById(id));
    }

    /** 我的自定义模板列表（含草稿/待审/上架）。 */
    public List<TemplateVO> myTemplates(Long userId) {
        return templateMapper.selectList(
                        new LambdaQueryWrapper<Template>()
                                .eq(Template::getType, "user")
                                .eq(Template::getOwnerUserId, userId)
                                .orderByDesc(Template::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /** 用户模板详情（归属校验）。 */
    public TemplateVO getUserTemplate(Long userId, Long id) {
        return toVO(owned(userId, id));
    }

    /** 删除用户模板（归属校验）。 */
    @Transactional
    public void deleteUserTemplate(Long userId, Long id) {
        Template t = owned(userId, id);
        templateMapper.deleteById(t.getId());
    }

    /** 提交上架（草稿→待审）。 */
    @Transactional
    public TemplateVO publish(Long userId, Long id) {
        Template t = owned(userId, id);
        if (t.getStatus() == STATUS_PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "模板已在审核中");
        }
        t.setStatus(STATUS_PENDING);
        templateMapper.updateById(t);
        return toVO(t);
    }

    // ---------------- 模板市场（R8-A3） ----------------

    /** 市场列表：上架的用户模板，支持搜索/筛选/分页。 */
    public PageResult<TemplateVO> marketList(long page, long size, String keyword, String category) {
        LambdaQueryWrapper<Template> qw = new LambdaQueryWrapper<Template>()
                .eq(Template::getType, "user")
                .eq(Template::getStatus, STATUS_ONLINE);
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Template::getName, keyword.trim());
        }
        if (category != null && !category.isBlank()) {
            qw.eq(Template::getCategory, category.trim());
        }
        qw.orderByDesc(Template::getUseCount).orderByDesc(Template::getId);
        Page<Template> p = templateMapper.selectPage(new Page<>(page, size), qw);
        List<TemplateVO> records = p.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(records, p.getTotal(), page, size);
    }

    /** 市场详情：上架用户模板，浏览数 +1。 */
    @Transactional
    public TemplateVO marketDetail(Long id) {
        Template t = templateMapper.selectOne(
                new LambdaQueryWrapper<Template>()
                        .eq(Template::getId, id)
                        .eq(Template::getType, "user")
                        .eq(Template::getStatus, STATUS_ONLINE));
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "模板不存在或未上架");
        }
        templateMapper.update(null, new LambdaUpdateWrapper<Template>()
                .eq(Template::getId, id)
                .setSql("view_count = view_count + 1"));
        t.setViewCount((t.getViewCount() == null ? 0 : t.getViewCount()) + 1);
        return toVO(t);
    }

    /** 一键使用：使用次数 +1，返回模板编码（前端据此应用到简历）。 */
    @Transactional
    public TemplateVO useTemplate(Long userId, Long id) {
        Template t = templateMapper.selectOne(
                new LambdaQueryWrapper<Template>()
                        .eq(Template::getId, id)
                        .eq(Template::getStatus, STATUS_ONLINE));
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "模板不存在或已下架");
        }
        templateMapper.update(null, new LambdaUpdateWrapper<Template>()
                .eq(Template::getId, id)
                .setSql("use_count = use_count + 1"));
        t.setUseCount((t.getUseCount() == null ? 0 : t.getUseCount()) + 1);
        return toVO(t);
    }

    // ---------------- 内部 ----------------

    private Template owned(Long userId, Long id) {
        Template t = templateMapper.selectById(id);
        if (t == null || !"user".equals(t.getType()) || !userId.equals(t.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "模板不存在");
        }
        return t;
    }

    private TemplateVO toVO(Template t) {
        TemplateVO vo = new TemplateVO();
        vo.setId(String.valueOf(t.getId()));
        vo.setCode(t.getCode());
        vo.setName(t.getName());
        vo.setType(t.getType());
        vo.setCategory(t.getCategory());
        vo.setSchema(t.getSchema());
        vo.setThumbnail(t.getThumbnail());
        vo.setOwnerUserId(t.getOwnerUserId() == null ? null : String.valueOf(t.getOwnerUserId()));
        vo.setVersion(t.getVersion());
        vo.setSchemaVersion(t.getSchemaVersion());
        vo.setUseCount(t.getUseCount());
        vo.setViewCount(t.getViewCount());
        vo.setAuditReason(t.getAuditReason());
        vo.setPublishedAt(t.getPublishedAt() == null ? null : t.getPublishedAt().format(FMT));
        vo.setSortOrder(t.getSortOrder());
        vo.setStatus(t.getStatus());
        vo.setCreatedAt(t.getCreatedAt() == null ? null : t.getCreatedAt().format(FMT));
        return vo;
    }
}
