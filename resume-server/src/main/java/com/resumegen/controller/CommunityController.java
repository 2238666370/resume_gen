package com.resumegen.controller;

import com.resumegen.ai.PublicRateLimiter;
import com.resumegen.common.ApiResponse;
import com.resumegen.common.PageResult;
import com.resumegen.dto.CommentRequest;
import com.resumegen.dto.CommentVO;
import com.resumegen.dto.InteractionVO;
import com.resumegen.dto.MyCommunityVO;
import com.resumegen.dto.MyPostVO;
import com.resumegen.dto.PostDetailVO;
import com.resumegen.dto.PostPublishRequest;
import com.resumegen.dto.PostVO;
import com.resumegen.dto.ReportRequest;
import com.resumegen.security.UserContext;
import com.resumegen.service.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 简历社区接口：浏览匿名可、互动需登录。
 */
@RestController
public class CommunityController {

    private static final long MAX_SIZE = 50;

    private final CommunityService communityService;
    private final PublicRateLimiter rateLimiter;

    public CommunityController(CommunityService communityService, PublicRateLimiter rateLimiter) {
        this.communityService = communityService;
        this.rateLimiter = rateLimiter;
    }

    // ---------- 匿名浏览（/api/public 放行） ----------

    @GetMapping("/api/public/community/posts")
    public ApiResponse<PageResult<PostVO>> posts(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            HttpServletRequest request) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        rateLimiter.check("ip:" + request.getRemoteAddr());
        return ApiResponse.ok(communityService.listPublic(page, size, tag, keyword, sort));
    }

    @GetMapping("/api/public/community/posts/{id}")
    public ApiResponse<PostDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(communityService.detailPublic(id));
    }

    @GetMapping("/api/public/community/posts/{id}/comments")
    public ApiResponse<List<CommentVO>> comments(@PathVariable Long id) {
        return ApiResponse.ok(communityService.listComments(id));
    }

    // ---------- 登录用户 ----------

    @PostMapping("/api/community/posts")
    public ApiResponse<MyPostVO> publish(@Valid @RequestBody PostPublishRequest req) {
        return ApiResponse.ok(communityService.publish(UserContext.userId(), req));
    }

    @GetMapping("/api/community/posts/{id}/interaction")
    public ApiResponse<InteractionVO> interaction(@PathVariable Long id) {
        return ApiResponse.ok(communityService.interaction(UserContext.userId(), id));
    }

    @PostMapping("/api/community/posts/{id}/like")
    public ApiResponse<InteractionVO> like(@PathVariable Long id) {
        return ApiResponse.ok(communityService.like(UserContext.userId(), id));
    }

    @PostMapping("/api/community/posts/{id}/collect")
    public ApiResponse<InteractionVO> collect(@PathVariable Long id) {
        return ApiResponse.ok(communityService.collect(UserContext.userId(), id));
    }

    @PostMapping("/api/community/posts/{id}/comments")
    public ApiResponse<CommentVO> comment(@PathVariable Long id,
                                          @Valid @RequestBody CommentRequest req) {
        return ApiResponse.ok(communityService.comment(UserContext.userId(), id, req));
    }

    @DeleteMapping("/api/community/comments/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable Long id) {
        communityService.deleteComment(UserContext.userId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/api/community/posts/{id}/report")
    public ApiResponse<Void> report(@PathVariable Long id,
                                    @Valid @RequestBody ReportRequest req) {
        communityService.report(UserContext.userId(), id, req);
        return ApiResponse.ok();
    }

    @GetMapping("/api/community/me")
    public ApiResponse<MyCommunityVO> my() {
        return ApiResponse.ok(communityService.my(UserContext.userId()));
    }
}