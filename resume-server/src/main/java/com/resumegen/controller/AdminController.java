package com.resumegen.controller;

import com.resumegen.common.ApiResponse;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.common.PageResult;
import com.resumegen.dto.AdminResumeItemVO;
import com.resumegen.dto.UserRoleRequest;
import com.resumegen.dto.UserStatusRequest;
import com.resumegen.dto.UserVO;
import com.resumegen.security.UserContext;
import com.resumegen.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final long MAX_SIZE = 100;

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    private void checkAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<UserVO>> users(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        checkAdmin();
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        return ApiResponse.ok(adminService.listUsers(page, size, keyword));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<Void> setUserStatus(@PathVariable Long id,
                                           @Valid @RequestBody UserStatusRequest req) {
        checkAdmin();
        adminService.setUserStatus(id, req.getStatus());
        return ApiResponse.ok();
    }

    @PutMapping("/users/{id}/role")
    public ApiResponse<Void> setUserRole(@PathVariable Long id,
                                         @Valid @RequestBody UserRoleRequest req) {
        checkAdmin();
        adminService.updateUserRole(id, req.getRole());
        return ApiResponse.ok();
    }

    @GetMapping("/users/{id}/resumes")
    public ApiResponse<PageResult<AdminResumeItemVO>> userResumes(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        checkAdmin();
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        return ApiResponse.ok(adminService.listUserResumes(id, page, size));
    }

    @GetMapping("/resumes")
    public ApiResponse<PageResult<AdminResumeItemVO>> resumes(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        checkAdmin();
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        return ApiResponse.ok(adminService.listAllResumes(page, size, keyword));
    }

    @DeleteMapping("/resumes/{id}")
    public ApiResponse<Void> deleteResume(@PathVariable String id) {
        checkAdmin();
        adminService.deleteResume(id);
        return ApiResponse.ok();
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        checkAdmin();
        return ApiResponse.ok(adminService.stats());
    }
}