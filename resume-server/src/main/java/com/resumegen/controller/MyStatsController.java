package com.resumegen.controller;

import com.resumegen.common.ApiResponse;
import com.resumegen.dto.CommentVO;
import com.resumegen.dto.MyResumeStatVO;
import com.resumegen.dto.MyStatsOverviewVO;
import com.resumegen.dto.TrendPointVO;
import com.resumegen.security.UserContext;
import com.resumegen.service.MyStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 我的数据后台（R9），需登录，强制 user_id 过滤。
 */
@RestController
@RequestMapping("/api/stats/my")
public class MyStatsController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MyStatsService myStatsService;

    public MyStatsController(MyStatsService myStatsService) {
        this.myStatsService = myStatsService;
    }

    @GetMapping("/overview")
    public ApiResponse<MyStatsOverviewVO> overview() {
        return ApiResponse.ok(myStatsService.overview(UserContext.userId()));
    }

    @GetMapping("/resumes")
    public ApiResponse<List<MyResumeStatVO>> resumes() {
        return ApiResponse.ok(myStatsService.resumes(UserContext.userId()));
    }

    @GetMapping("/resumes/{id}/trend")
    public ApiResponse<List<TrendPointVO>> trend(
            @PathVariable String id,
            @RequestParam(defaultValue = "hour") String granularity,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime end = parseOrDefault(to, LocalDateTime.now());
        LocalDateTime start = parseOrDefault(from, end.minusHours(24));
        return ApiResponse.ok(myStatsService.trend(UserContext.userId(), id, granularity, start, end));
    }

    @GetMapping("/resumes/{id}/comments")
    public ApiResponse<List<CommentVO>> comments(@PathVariable String id) {
        return ApiResponse.ok(myStatsService.comments(UserContext.userId(), id));
    }

    private LocalDateTime parseOrDefault(String s, LocalDateTime def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            return LocalDateTime.parse(s, FMT);
        } catch (Exception e) {
            return def;
        }
    }
}
