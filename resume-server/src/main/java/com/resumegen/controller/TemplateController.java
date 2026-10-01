package com.resumegen.controller;

import com.resumegen.common.ApiResponse;
import com.resumegen.common.PageResult;
import com.resumegen.dto.TemplateSaveRequest;
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

import java.util.List;

/**
 * 简历模板（公开列表/详情 + 用户自定义模板 + 模板市场）。
 */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private static final long MAX_SIZE = 50;

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    // ---------------- 公开（匿名可访问） ----------------

    @GetMapping
    public ApiResponse<List<TemplateVO>> list() {
        return ApiResponse.ok(templateService.listPublic());
    }

    @GetMapping("/detail/{code}")
    public ApiResponse<TemplateVO> detail(@PathVariable String code) {
        return ApiResponse.ok(templateService.getByCode(code));
    }

    @GetMapping("/market")
    public ApiResponse<PageResult<TemplateVO>> market(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        return ApiResponse.ok(templateService.marketList(page, size, keyword, category));
    }

    @GetMapping("/market/{id}")
    public ApiResponse<TemplateVO> marketDetail(@PathVariable Long id) {
        return ApiResponse.ok(templateService.marketDetail(id));
    }

    // ---------------- 用户（需登录） ----------------

    @GetMapping("/my")
    public ApiResponse<List<TemplateVO>> my() {
        return ApiResponse.ok(templateService.myTemplates(UserContext.requireUserId()));
    }

    @PostMapping
    public ApiResponse<TemplateVO> create(@Valid @RequestBody TemplateSaveRequest req) {
        return ApiResponse.ok(templateService.createUserTemplate(UserContext.requireUserId(), req));
    }

    @PutMapping("/{id}")
    public ApiResponse<TemplateVO> update(@PathVariable Long id,
                                          @Valid @RequestBody TemplateSaveRequest req) {
        return ApiResponse.ok(templateService.updateUserTemplate(UserContext.requireUserId(), id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        templateService.deleteUserTemplate(UserContext.requireUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<TemplateVO> publish(@PathVariable Long id) {
        return ApiResponse.ok(templateService.publish(UserContext.requireUserId(), id));
    }

    @PostMapping("/{id}/use")
    public ApiResponse<TemplateVO> use(@PathVariable Long id) {
        return ApiResponse.ok(templateService.useTemplate(UserContext.requireUserId(), id));
    }
}
