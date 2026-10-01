package com.resumegen.controller;

import com.resumegen.common.ApiResponse;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.common.PageResult;
import com.resumegen.dto.AdminPostItemVO;
import com.resumegen.dto.AdminReportItemVO;
import com.resumegen.dto.AuditRequest;
import com.resumegen.security.UserContext;
import com.resumegen.service.CommunityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区治理：人工复审、下架、删除、举报处理（仅管理员）。
 */
@RestController
@RequestMapping("/admin/community")
public class AdminCommunityController {

    private static final long MAX_SIZE = 100;

    private final CommunityService communityService;

    public AdminCommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    private void checkAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    @GetMapping("/posts")
    public ApiResponse<PageResult<AdminPostItemVO>> posts(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status) {
        checkAdmin();
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        return ApiResponse.ok(communityService.adminList(page, size, status));
    }

    @PutMapping("/posts/{id}/audit")
    public ApiResponse<Void> audit(@PathVariable Long id,
                                   @Valid @RequestBody AuditRequest req) {
        checkAdmin();
        communityService.adminAudit(id, req);
        return ApiResponse.ok();
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        checkAdmin();
        communityService.adminDelete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/reports")
    public ApiResponse<PageResult<AdminReportItemVO>> reports(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        checkAdmin();
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        return ApiResponse.ok(communityService.adminReports(page, size));
    }

    @PutMapping("/reports/{id}")
    public ApiResponse<Void> resolveReport(@PathVariable Long id) {
        checkAdmin();
        communityService.adminResolveReport(id);
        return ApiResponse.ok();
    }
}