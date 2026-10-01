package com.resumegen.controller;

import com.resumegen.common.ApiResponse;
import com.resumegen.dto.AiHistoryVO;
import com.resumegen.security.UserContext;
import com.resumegen.service.AiHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 产出历史查询。
 */
@RestController
@RequestMapping("/api/ai/history")
public class AiHistoryController {

    private final AiHistoryService historyService;

    public AiHistoryController(AiHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ApiResponse<List<AiHistoryVO>> list(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(historyService.list(UserContext.userId(), taskType, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<AiHistoryVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(historyService.detail(UserContext.userId(), id));
    }
}