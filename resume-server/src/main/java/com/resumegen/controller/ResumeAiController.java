package com.resumegen.controller;

import com.resumegen.ai.AiAsyncService;
import com.resumegen.ai.AiRateLimiter;
import com.resumegen.common.ApiResponse;
import com.resumegen.dto.AiTaskSubmitVO;
import com.resumegen.dto.ResumeAiRequest;
import com.resumegen.dto.ResumeApplyRequest;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.dto.ResumeExpandVO;
import com.resumegen.dto.ResumeRewriteVO;
import com.resumegen.dto.ResumeScoreRequest;
import com.resumegen.dto.ResumeScoreVO;
import com.resumegen.dto.ResumeSuggestionVO;
import com.resumegen.security.UserContext;
import com.resumegen.service.ResumeAiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 修改 / 完善简历（R6）。
 */
@RestController
@RequestMapping("/api/ai/resume")
public class ResumeAiController {

    private final ResumeAiService resumeAiService;
    private final AiAsyncService aiAsyncService;
    private final AiRateLimiter rateLimiter;

    public ResumeAiController(ResumeAiService resumeAiService, AiAsyncService aiAsyncService,
                              AiRateLimiter rateLimiter) {
        this.resumeAiService = resumeAiService;
        this.aiAsyncService = aiAsyncService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/rewrite")
    public ApiResponse<ResumeRewriteVO> rewrite(@RequestBody ResumeAiRequest req) {
        rateLimiter.check(UserContext.userId(), AiAsyncService.TASK_REWRITE);
        return ApiResponse.ok(resumeAiService.rewrite(UserContext.userId(), req));
    }

    @PostMapping("/rewrite/async")
    public ApiResponse<AiTaskSubmitVO> rewriteAsync(@RequestBody ResumeAiRequest req) {
        rateLimiter.check(UserContext.userId(), AiAsyncService.TASK_REWRITE);
        return ApiResponse.ok(aiAsyncService.submitRewrite(UserContext.userId(), req));
    }

    @PostMapping("/expand")
    public ApiResponse<ResumeExpandVO> expand(@RequestBody ResumeAiRequest req) {
        rateLimiter.check(UserContext.userId(), AiAsyncService.TASK_EXPAND);
        return ApiResponse.ok(resumeAiService.expand(UserContext.userId(), req));
    }

    @PostMapping("/expand/async")
    public ApiResponse<AiTaskSubmitVO> expandAsync(@RequestBody ResumeAiRequest req) {
        rateLimiter.check(UserContext.userId(), AiAsyncService.TASK_EXPAND);
        return ApiResponse.ok(aiAsyncService.submitExpand(UserContext.userId(), req));
    }

    @PostMapping("/suggest")
    public ApiResponse<List<ResumeSuggestionVO>> suggest(@RequestBody ResumeAiRequest req) {
        rateLimiter.check(UserContext.userId(), AiAsyncService.TASK_SUGGEST);
        return ApiResponse.ok(resumeAiService.suggest(UserContext.userId(), req));
    }

    @PostMapping("/suggest/async")
    public ApiResponse<AiTaskSubmitVO> suggestAsync(@RequestBody ResumeAiRequest req) {
        rateLimiter.check(UserContext.userId(), AiAsyncService.TASK_SUGGEST);
        return ApiResponse.ok(aiAsyncService.submitSuggest(UserContext.userId(), req));
    }

    @PostMapping("/apply")
    public ApiResponse<ResumeDetailVO> apply(@Valid @RequestBody ResumeApplyRequest req) {
        return ApiResponse.ok(resumeAiService.apply(UserContext.userId(), req));
    }

    @PostMapping("/improve")
    public ApiResponse<ResumeDTO> improve(@RequestBody ResumeAiRequest req) {
        rateLimiter.check(UserContext.userId(), AiAsyncService.TASK_IMPROVE);
        return ApiResponse.ok(resumeAiService.improve(UserContext.userId(), req));
    }

    @PostMapping("/improve/async")
    public ApiResponse<AiTaskSubmitVO> improveAsync(@RequestBody ResumeAiRequest req) {
        rateLimiter.check(UserContext.userId(), AiAsyncService.TASK_IMPROVE);
        return ApiResponse.ok(aiAsyncService.submitImprove(UserContext.userId(), req));
    }

    @PostMapping("/score")
    public ApiResponse<ResumeScoreVO> score(@Valid @RequestBody ResumeScoreRequest req) {
        rateLimiter.check(UserContext.userId(), AiAsyncService.TASK_SCORE);
        return ApiResponse.ok(resumeAiService.score(UserContext.userId(), req));
    }

    @PostMapping("/score/async")
    public ApiResponse<AiTaskSubmitVO> scoreAsync(@Valid @RequestBody ResumeScoreRequest req) {
        rateLimiter.check(UserContext.userId(), AiAsyncService.TASK_SCORE);
        return ApiResponse.ok(aiAsyncService.submitScore(UserContext.userId(), req));
    }
}