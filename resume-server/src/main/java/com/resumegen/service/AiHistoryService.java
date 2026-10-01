package com.resumegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumegen.ai.AiSupport;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.dto.AiHistoryVO;
import com.resumegen.entity.AiGenerationLog;
import com.resumegen.entity.Resume;
import com.resumegen.mapper.AiGenerationLogMapper;
import com.resumegen.mapper.ResumeMapper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI 产出历史查询（R5/R6 共用）：读取 ai_generation_log 并提供列表与详情。
 */
@Service
public class AiHistoryService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int SUMMARY_LEN = 200;

    private final AiGenerationLogMapper logMapper;
    private final ResumeMapper resumeMapper;

    public AiHistoryService(AiGenerationLogMapper logMapper, ResumeMapper resumeMapper) {
        this.logMapper = logMapper;
        this.resumeMapper = resumeMapper;
    }

    /** 历史列表（可选按 taskType 过滤，order by id desc）。 */
    public List<AiHistoryVO> list(Long userId, String taskType, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        LambdaQueryWrapper<AiGenerationLog> w = new LambdaQueryWrapper<AiGenerationLog>()
                .eq(AiGenerationLog::getUserId, userId)
                .orderByDesc(AiGenerationLog::getId)
                .last("LIMIT " + size);
        if (taskType != null && !taskType.isBlank()) {
            w.eq(AiGenerationLog::getTaskType, taskType.trim());
        }
        List<AiGenerationLog> logs = logMapper.selectList(w);
        Map<Long, String> titles = resumeTitles(logs);
        return logs.stream()
                .map(g -> toVO(g, titles, true))
                .collect(Collectors.toList());
    }

    /** 历史详情（完整输出，校验归属）。 */
    public AiHistoryVO detail(Long userId, Long id) {
        AiGenerationLog g = logMapper.selectOne(new LambdaQueryWrapper<AiGenerationLog>()
                .eq(AiGenerationLog::getId, id)
                .eq(AiGenerationLog::getUserId, userId));
        if (g == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return toVO(g, resumeTitles(List.of(g)), false);
    }

    private AiHistoryVO toVO(AiGenerationLog g, Map<Long, String> titles, boolean summaryOnly) {
        AiHistoryVO vo = new AiHistoryVO();
        vo.setId(String.valueOf(g.getId()));
        vo.setResumeId(g.getResumeId() == null ? null : String.valueOf(g.getResumeId()));
        vo.setResumeTitle(g.getResumeId() == null ? null : titles.get(g.getResumeId()));
        vo.setTaskType(g.getTaskType());
        vo.setTokenUsage(g.getTokenUsage());
        vo.setCreatedAt(g.getCreatedAt() == null ? null : g.getCreatedAt().format(FMT));
        if (summaryOnly) {
            vo.setSummary(AiSupport.truncate(g.getOutput(), SUMMARY_LEN));
        } else {
            vo.setOutput(g.getOutput());
        }
        return vo;
    }

    private Map<Long, String> resumeTitles(List<AiGenerationLog> logs) {
        List<Long> ids = logs.stream()
                .map(AiGenerationLog::getResumeId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> titles = new HashMap<>();
        if (ids.isEmpty()) {
            return titles;
        }
        for (Resume r : resumeMapper.selectBatchIds(ids)) {
            titles.put(r.getId(), r.getTitle());
        }
        return titles;
    }
}