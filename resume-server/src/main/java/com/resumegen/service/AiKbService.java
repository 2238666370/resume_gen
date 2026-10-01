package com.resumegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumegen.ai.AiKnowledgeService;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.dto.AiKbEntryRequest;
import com.resumegen.dto.AiKbEntryVO;
import com.resumegen.entity.AiKbEntry;
import com.resumegen.mapper.AiKbEntryMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 知识库 / skill prompt 维护（管理端）。变更后使 RAG 索引失效以在下一次检索时重建。
 */
@Service
public class AiKbService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiKbEntryMapper kbMapper;
    private final AiKnowledgeService knowledgeService;

    public AiKbService(AiKbEntryMapper kbMapper, AiKnowledgeService knowledgeService) {
        this.kbMapper = kbMapper;
        this.knowledgeService = knowledgeService;
    }

    public List<AiKbEntryVO> list(String kbType) {
        LambdaQueryWrapper<AiKbEntry> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(kbType)) {
            qw.eq(AiKbEntry::getKbType, kbType);
        }
        qw.orderByAsc(AiKbEntry::getKbType).orderByAsc(AiKbEntry::getId);
        List<AiKbEntryVO> result = new ArrayList<>();
        for (AiKbEntry e : kbMapper.selectList(qw)) {
            result.add(toVO(e));
        }
        return result;
    }

    public AiKbEntryVO create(AiKbEntryRequest req) {
        AiKbEntry e = new AiKbEntry();
        apply(e, req);
        kbMapper.insert(e);
        knowledgeService.invalidate();
        return toVO(e);
    }

    public AiKbEntryVO update(Long id, AiKbEntryRequest req) {
        AiKbEntry e = kbMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        apply(e, req);
        kbMapper.updateById(e);
        knowledgeService.invalidate();
        return toVO(e);
    }

    public void delete(Long id) {
        if (kbMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        kbMapper.deleteById(id);
        knowledgeService.invalidate();
    }

    private void apply(AiKbEntry e, AiKbEntryRequest req) {
        e.setKbType(req.getKbType());
        e.setCode(req.getCode());
        e.setTitle(req.getTitle());
        e.setContent(req.getContent());
        e.setMetadata(req.getMetadata());
        e.setStatus(req.getStatus() == null ? 1 : req.getStatus());
    }

    private AiKbEntryVO toVO(AiKbEntry e) {
        AiKbEntryVO vo = new AiKbEntryVO();
        vo.setId(String.valueOf(e.getId()));
        vo.setKbType(e.getKbType());
        vo.setCode(e.getCode());
        vo.setTitle(e.getTitle());
        vo.setContent(e.getContent());
        vo.setMetadata(e.getMetadata());
        vo.setStatus(e.getStatus());
        vo.setCreatedAt(e.getCreatedAt() == null ? null : e.getCreatedAt().format(FMT));
        return vo;
    }
}