package com.resumegen.controller;

import com.resumegen.common.ApiResponse;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.dto.AiKbEntryRequest;
import com.resumegen.dto.AiKbEntryVO;
import com.resumegen.security.UserContext;
import com.resumegen.service.AiKbService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端 AI 知识库维护（R5/R6 共用）。
 */
@RestController
@RequestMapping("/admin/ai/kb")
public class AdminKbController {

    private final AiKbService aiKbService;

    public AdminKbController(AiKbService aiKbService) {
        this.aiKbService = aiKbService;
    }

    private void checkAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    @GetMapping
    public ApiResponse<List<AiKbEntryVO>> list(@RequestParam(required = false) String kbType) {
        checkAdmin();
        return ApiResponse.ok(aiKbService.list(kbType));
    }

    @PostMapping
    public ApiResponse<AiKbEntryVO> create(@Valid @RequestBody AiKbEntryRequest req) {
        checkAdmin();
        return ApiResponse.ok(aiKbService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<AiKbEntryVO> update(@PathVariable Long id,
                                           @Valid @RequestBody AiKbEntryRequest req) {
        checkAdmin();
        return ApiResponse.ok(aiKbService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        checkAdmin();
        aiKbService.delete(id);
        return ApiResponse.ok();
    }
}