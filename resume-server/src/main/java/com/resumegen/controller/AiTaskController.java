package com.resumegen.controller;

import com.resumegen.ai.AiAsyncService;
import com.resumegen.common.ApiResponse;
import com.resumegen.dto.AiTaskVO;
import com.resumegen.security.UserContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 异步 AI 任务查询（轮询）与完成推送（SSE）。
 */
@RestController
@RequestMapping("/api/ai/task")
public class AiTaskController {

    private final AiAsyncService aiAsyncService;

    public AiTaskController(AiAsyncService aiAsyncService) {
        this.aiAsyncService = aiAsyncService;
    }

    @GetMapping("/{taskId}")
    public ApiResponse<AiTaskVO> get(@PathVariable String taskId) {
        return ApiResponse.ok(aiAsyncService.get(taskId));
    }

    /**
     * SSE 推送：任务完成（SUCCESS / FAILED）后向浏览器推送一条结果事件并关闭流。
     * 前端用 EventSource 订阅，无法自定义请求头，故 token 走 ?token= 查询参数。
     */
    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String taskId) {
        Long userId = UserContext.userId();
        SseEmitter emitter = new SseEmitter(0L);
        aiAsyncService.subscribe(taskId, userId, (vo) -> {
            try {
                emitter.send(SseEmitter.event().data(vo, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}