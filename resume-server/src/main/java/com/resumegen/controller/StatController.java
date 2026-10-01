package com.resumegen.controller;

import com.resumegen.common.ApiResponse;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.dto.PvUvVO;
import com.resumegen.dto.StatsOverviewVO;
import com.resumegen.dto.TrackRequest;
import com.resumegen.dto.TrendPointVO;
import com.resumegen.security.UserContext;
import com.resumegen.service.StatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 埋点上上报 + 管理后台流量报表。
 */
@RestController
public class StatController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StatService statService;

    public StatController(StatService statService) {
        this.statService = statService;
    }

    private void checkAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /** 匿名/分享页埋点：无 token，按 deviceId 去重。 */
    @PostMapping("/api/public/track")
    public ApiResponse<Void> trackPublic(@RequestBody(required = false) TrackRequest req) {
        statService.track(req, null);
        return ApiResponse.ok();
    }

    /** 登录用户埋点：按 userId 去重。 */
    @PostMapping("/api/stats/track")
    public ApiResponse<Void> trackAuthed(@RequestBody(required = false) TrackRequest req) {
        statService.track(req, UserContext.userId());
        return ApiResponse.ok();
    }

    @GetMapping("/admin/stats/overview")
    public ApiResponse<StatsOverviewVO> overview() {
        checkAdmin();
        return ApiResponse.ok(statService.overview());
    }

    @GetMapping("/admin/stats/trend")
    public ApiResponse<List<TrendPointVO>> trend(
            @RequestParam(defaultValue = "hour") String granularity,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        checkAdmin();
        LocalDateTime end = parseOrDefault(to, LocalDateTime.now());
        LocalDateTime start = parseOrDefault(from, end.minusHours(24));
        return ApiResponse.ok(statService.trend(granularity, start, end));
    }

    @GetMapping("/admin/stats/pvuv")
    public ApiResponse<PvUvVO> pvuv(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        checkAdmin();
        LocalDateTime end = parseOrDefault(to, LocalDateTime.now());
        LocalDateTime start = parseOrDefault(from, end.minusDays(7));
        return ApiResponse.ok(statService.pvuv(start, end));
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