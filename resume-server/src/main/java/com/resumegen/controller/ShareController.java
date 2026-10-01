package com.resumegen.controller;

import com.resumegen.common.ApiResponse;
import com.resumegen.ai.PublicRateLimiter;
import com.resumegen.dto.PublicShareVO;
import com.resumegen.dto.ShareCreateRequest;
import com.resumegen.dto.ShareOgVO;
import com.resumegen.dto.ShareVO;
import com.resumegen.security.UserContext;
import com.resumegen.service.ShareService;
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

@RestController
public class ShareController {

    private final ShareService shareService;
    private final PublicRateLimiter rateLimiter;

    public ShareController(ShareService shareService, PublicRateLimiter rateLimiter) {
        this.shareService = shareService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/api/shares")
    public ApiResponse<ShareVO> create(@Valid @RequestBody ShareCreateRequest req) {
        return ApiResponse.ok(shareService.create(UserContext.userId(), req));
    }

    @GetMapping("/api/shares")
    public ApiResponse<List<ShareVO>> list() {
        return ApiResponse.ok(shareService.list(UserContext.userId()));
    }

    @DeleteMapping("/api/shares/{key}")
    public ApiResponse<Void> revoke(@PathVariable String key) {
        shareService.revoke(UserContext.userId(), key);
        return ApiResponse.ok();
    }

    @GetMapping("/api/public/shares/{key}")
    public ApiResponse<PublicShareVO> view(@PathVariable String key,
                                           @RequestParam(required = false) String password,
                                           HttpServletRequest request) {
        rateLimiter.check("ip:" + request.getRemoteAddr());
        return ApiResponse.ok(shareService.publicRead(key, password));
    }

    @GetMapping("/api/public/og/{key}")
    public ApiResponse<ShareOgVO> og(@PathVariable String key) {
        return ApiResponse.ok(shareService.publicOg(key));
    }
}