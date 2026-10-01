package com.resumegen.controller;

import com.resumegen.ai.AiAsyncService;
import com.resumegen.ai.AiRateLimiter;
import com.resumegen.common.ApiResponse;
import com.resumegen.dto.AiTaskSubmitVO;
import com.resumegen.dto.InterviewGenerateRequest;
import com.resumegen.dto.InterviewRenameRequest;
import com.resumegen.dto.InterviewSetVO;
import com.resumegen.security.UserContext;
import com.resumegen.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面试题库（R5）。
 */
@RestController
@RequestMapping("/api/ai/interview")
public class InterviewController {

    private final InterviewService interviewService;
    private final AiAsyncService aiAsyncService;
    private final AiRateLimiter rateLimiter;

    public InterviewController(InterviewService interviewService, AiAsyncService aiAsyncService,
                               AiRateLimiter rateLimiter) {
        this.interviewService = interviewService;
        this.aiAsyncService = aiAsyncService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/generate")
    public ApiResponse<InterviewSetVO> generate(@Valid @RequestBody InterviewGenerateRequest req) {
        rateLimiter.check(UserContext.userId(), AiAsyncService.TASK_INTERVIEW);
        return ApiResponse.ok(interviewService.generate(UserContext.userId(), req));
    }

    @PostMapping("/async")
    public ApiResponse<AiTaskSubmitVO> generateAsync(@Valid @RequestBody InterviewGenerateRequest req) {
        rateLimiter.check(UserContext.userId(), AiAsyncService.TASK_INTERVIEW);
        return ApiResponse.ok(aiAsyncService.submitInterview(UserContext.userId(), req));
    }

    @GetMapping("/sets")
    public ApiResponse<List<InterviewSetVO>> list() {
        return ApiResponse.ok(interviewService.list(UserContext.userId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<InterviewSetVO> get(@PathVariable Long id) {
        return ApiResponse.ok(interviewService.get(UserContext.userId(), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        interviewService.delete(UserContext.userId(), id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/title")
    public ApiResponse<InterviewSetVO> rename(@PathVariable Long id,
                                              @Valid @RequestBody InterviewRenameRequest req) {
        return ApiResponse.ok(interviewService.rename(UserContext.userId(), id, req.getTitle()));
    }
}