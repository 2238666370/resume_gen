package com.resumegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.dto.ResumeSnapshotVO;
import com.resumegen.dto.ResumeVersionDiffVO;
import com.resumegen.entity.ResumeSnapshot;
import com.resumegen.mapper.ResumeSnapshotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 简历版本快照（R8-C 时光机）：记录、列表、详情、diff。
 * 回滚在控制器层编排（读快照内容 → 复用 {@link ResumeService#update} 写回，避免服务间循环依赖）。
 */
@Service
public class ResumeSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(ResumeSnapshotService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ResumeSnapshotMapper snapshotMapper;
    private final ObjectMapper om;

    public ResumeSnapshotService(ResumeSnapshotMapper snapshotMapper, ObjectMapper om) {
        this.snapshotMapper = snapshotMapper;
        this.om = om;
    }

    /** 记录一份快照（保存链路内调用，尽力而为，失败不阻断主流程）。 */
    public void record(Long userId, String resumeId, int version, String source, ResumeDTO content) {
        try {
            ResumeSnapshot s = new ResumeSnapshot();
            s.setUserId(userId);
            s.setResumeId(Long.parseLong(resumeId));
            s.setVersion(version);
            s.setSource(source == null || source.isBlank() ? "manual" : source);
            s.setContent(om.writeValueAsString(content));
            snapshotMapper.insert(s);
        } catch (Exception e) {
            log.warn("简历快照写入失败 resumeId={}, version={}", resumeId, version, e);
        }
    }

    /** 历史快照列表（按 version 倒序，不含完整内容）。 */
    public List<ResumeSnapshotVO> list(Long userId, String resumeId) {
        long rid = parseResumeId(resumeId);
        List<ResumeSnapshot> list = snapshotMapper.selectList(new LambdaQueryWrapper<ResumeSnapshot>()
                .eq(ResumeSnapshot::getUserId, userId)
                .eq(ResumeSnapshot::getResumeId, rid)
                .orderByDesc(ResumeSnapshot::getVersion));
        List<ResumeSnapshotVO> out = new ArrayList<>();
        for (ResumeSnapshot s : list) {
            out.add(toVO(s, false));
        }
        return out;
    }

    /** 快照详情（完整内容，校验归属）。 */
    public ResumeSnapshotVO get(Long userId, String resumeId, Long snapshotId) {
        ResumeSnapshot s = owned(userId, resumeId, snapshotId);
        return toVO(s, true);
    }

    /** 取快照完整内容（回滚用）。 */
    public ResumeDTO getContent(Long userId, String resumeId, Long snapshotId) {
        ResumeSnapshot s = owned(userId, resumeId, snapshotId);
        return parseContent(s);
    }

    /** 两个快照字段级 diff 的原始内容。 */
    public ResumeVersionDiffVO diff(Long userId, String resumeId, Long fromId, Long toId) {
        ResumeSnapshot from = owned(userId, resumeId, fromId);
        ResumeSnapshot to = owned(userId, resumeId, toId);
        ResumeVersionDiffVO vo = new ResumeVersionDiffVO();
        vo.setFromVersion(from.getVersion());
        vo.setToVersion(to.getVersion());
        vo.setFrom(parseContent(from));
        vo.setTo(parseContent(to));
        return vo;
    }

    private ResumeSnapshot owned(Long userId, String resumeId, Long snapshotId) {
        long rid = parseResumeId(resumeId);
        ResumeSnapshot s = snapshotMapper.selectById(snapshotId);
        if (s == null || !userId.equals(s.getUserId()) || !s.getResumeId().equals(rid)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return s;
    }

    private ResumeSnapshotVO toVO(ResumeSnapshot s, boolean withContent) {
        ResumeSnapshotVO vo = new ResumeSnapshotVO();
        vo.setId(String.valueOf(s.getId()));
        vo.setResumeId(String.valueOf(s.getResumeId()));
        vo.setVersion(s.getVersion());
        vo.setSource(s.getSource());
        vo.setCreatedAt(s.getCreatedAt() == null ? null : s.getCreatedAt().format(FMT));
        if (withContent) {
            vo.setContent(parseContent(s));
        }
        return vo;
    }

    private ResumeDTO parseContent(ResumeSnapshot s) {
        try {
            return om.readValue(s.getContent(), ResumeDTO.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "快照内容解析失败");
        }
    }

    private long parseResumeId(String resumeId) {
        try {
            return Long.parseLong(resumeId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }
}
