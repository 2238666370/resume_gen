package com.resumegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.cache.CacheService;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.common.PageResult;
import com.resumegen.config.ResumeProperties;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.dto.ResumeListItemVO;
import com.resumegen.repository.ResumeRepository;
import com.resumegen.search.SearchHits;
import com.resumegen.search.SearchIndexSyncService;
import com.resumegen.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 简历业务：封装存储抽象，叠加缓存（Cache-Aside）与乐观锁冲突处理。
 */
@Service
public class ResumeService {

    private static final String[] DEFAULT_SECTIONS = {
            "personal", "education", "experience", "internship",
            "skills", "projects", "certificates", "languages"
    };

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeRepository repository;
    private final CacheService cacheService;
    private final ResumeProperties props;
    private final ObjectMapper objectMapper;
    private final ProfileService profileService;
    private final ResumeSnapshotService snapshotService;
    private final SearchService searchService;
    private final SearchIndexSyncService searchIndexSync;

    public ResumeService(ResumeRepository repository, CacheService cacheService,
                         ResumeProperties props, ObjectMapper objectMapper, ProfileService profileService,
                         ResumeSnapshotService snapshotService, SearchService searchService,
                         SearchIndexSyncService searchIndexSync) {
        this.repository = repository;
        this.cacheService = cacheService;
        this.props = props;
        this.objectMapper = objectMapper;
        this.profileService = profileService;
        this.snapshotService = snapshotService;
        this.searchService = searchService;
        this.searchIndexSync = searchIndexSync;
    }

    private String detailKey(String resumeId) {
        return props.getCache().getPrefix() + "detail:" + resumeId;
    }

    // ---------- 查询 ----------

    public PageResult<ResumeListItemVO> list(Long userId, long page, long size, String keyword) {
        // ES 检索优先（active 且命中），失败/未启用时降级 MySQL LIKE
        if (searchService.active() && keyword != null && !keyword.isBlank()) {
            SearchHits<Long> hits = searchService.searchResumes(userId, keyword, page, size);
            if (hits != null) {
                List<String> ids = new ArrayList<>();
                for (Long id : hits.getIds()) {
                    ids.add(String.valueOf(id));
                }
                List<ResumeListItemVO> records = repository.listByIds(userId, ids);
                return new PageResult<>(records, hits.getTotal(), page, size);
            }
        }
        long total = repository.count(userId, keyword);
        List<ResumeListItemVO> records = repository.list(userId, page, size, keyword);
        return new PageResult<>(records, total, page, size);
    }

    public ResumeDetailVO get(Long userId, String resumeId) {
        String key = detailKey(resumeId);
        String cached = cacheService.get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ResumeDetailVO.class);
            } catch (Exception ignored) {
                // 反序列化失败则回源，忽略
            }
        }
        ResumeDetailVO vo = repository.get(userId, resumeId);
        try {
            cacheService.set(key, objectMapper.writeValueAsString(vo), props.getCache().getTtlSeconds());
        } catch (Exception ignored) {
            // 缓存写入失败不影响主流程
        }
        return vo;
    }

    // ---------- 写 ----------

    public ResumeDetailVO create(Long userId, String title, String templateId) {
        ResumeDTO dto = defaultData(title);
        if (templateId != null && !templateId.isBlank()) {
            dto.setTemplateId(templateId);
        }
        dto.setPersonal(profileService.getPersonal(userId));
        return repository.create(userId, dto);
    }

    public ResumeDetailVO update(Long userId, String resumeId, ResumeDTO dto, Integer expectedVersion) {
        return update(userId, resumeId, dto, expectedVersion, "manual");
    }

    public ResumeDetailVO update(Long userId, String resumeId, ResumeDTO dto, Integer expectedVersion, String source) {
        boolean ok = repository.update(userId, resumeId, dto, expectedVersion);
        if (!ok) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "数据已被他人修改，请刷新后重试");
        }
        cacheService.delete(detailKey(resumeId));
        ResumeDetailVO vo = get(userId, resumeId);
        snapshotService.record(userId, resumeId, vo.getVersion(), source, dto);
        syncIndex(userId, resumeId);
        return vo;
    }

    /** 同步简历到检索索引（json 存储的非数字 id 跳过，ES 索引以数字 id 为文档 id）。 */
    private void syncIndex(Long userId, String resumeId) {
        try {
            searchIndexSync.syncResume(userId, Long.parseLong(resumeId));
        } catch (NumberFormatException ignored) {
            // json 存储的 UUID id 不参与 ES 索引
        }
    }

    /** 局部更新：加载当前内容后仅覆盖请求中出现的字段。 */
    public ResumeDetailVO patch(Long userId, String resumeId, String body, Integer expectedVersion) {
        return patch(userId, resumeId, body, expectedVersion, "manual");
    }

    public ResumeDetailVO patch(Long userId, String resumeId, String body, Integer expectedVersion, String source) {
        ResumeDetailVO current = get(userId, resumeId);
        ResumeDTO merged = objectMapper.convertValue(current, ResumeDTO.class);
        try {
            merged = objectMapper.readerForUpdating(merged).readValue(body);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "请求体格式错误");
        }
        return update(userId, resumeId, merged, expectedVersion, source);
    }

    public void delete(Long userId, String resumeId) {
        boolean ok = repository.delete(userId, resumeId);
        if (!ok) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        cacheService.delete(detailKey(resumeId));
        try {
            searchIndexSync.syncDeleteResume(Long.parseLong(resumeId));
        } catch (NumberFormatException ignored) {
            // json 存储的 UUID id 不参与 ES 索引
        }
    }

    // ---------- 导入/导出 ----------

    public ResumeDTO exportJson(Long userId, String resumeId) {
        // 仅导出内容（剥离 id/version/updatedAt 元信息）
        return objectMapper.convertValue(get(userId, resumeId), ResumeDTO.class);
    }

    public ResumeDetailVO importJson(Long userId, ResumeDTO dto) {
        return repository.create(userId, dto);
    }

    // ---------- 内部 ----------

    private ResumeDTO defaultData(String title) {
        ResumeDTO dto = new ResumeDTO();
        dto.setTitle(title == null || title.isBlank() ? "未命名简历" : title);
        List<ResumeDTO.Section> sections = new ArrayList<>();
        for (int i = 0; i < DEFAULT_SECTIONS.length; i++) {
            ResumeDTO.Section s = new ResumeDTO.Section();
            s.setId(DEFAULT_SECTIONS[i]);
            s.setType(DEFAULT_SECTIONS[i]);
            s.setVisible(true);
            s.setOrder(i);
            sections.add(s);
        }
        dto.setSections(sections);
        return dto;
    }
}