package com.resumegen.controller;

import com.resumegen.common.ApiResponse;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.common.PageResult;
import com.resumegen.dto.TemplateCreateRequest;
import com.resumegen.dto.TemplateUpdateRequest;
import com.resumegen.dto.TemplateVO;
import com.resumegen.security.UserContext;
import com.resumegen.service.TemplateService;
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

/**
 * 模板治理（仅管理员）：列表 / 新增 / 更新(上下架) / 删除。
 */
@RestController
@RequestMapping("/admin/templates")
public class AdminTemplateController {

    private static final long MAX_SIZE = 100;

    private final TemplateService templateService;

    public AdminTemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    private void checkAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    @GetMapping
    public ApiResponse<PageResult<TemplateVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        checkAdmin();
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        return ApiResponse.ok(templateService.adminList(page, size));
    }

    @PostMapping
    public ApiResponse<TemplateVO> create(@Valid @RequestBody TemplateCreateRequest req) {
        checkAdmin();
        return ApiResponse.ok(templateService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<TemplateVO> update(@PathVariable Long id,
                                          @Valid @RequestBody TemplateUpdateRequest req) {
        checkAdmin();
        return ApiResponse.ok(templateService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        checkAdmin();
        templateService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/audit")
    public ApiResponse<TemplateVO> audit(@PathVariable Long id,
                                         @RequestParam boolean approve,
                                         @RequestParam(required = false) String reason) {
        checkAdmin();
        return ApiResponse.ok(templateService.audit(id, approve, reason));
    }
}