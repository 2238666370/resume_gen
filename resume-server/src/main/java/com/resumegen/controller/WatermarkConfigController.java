package com.resumegen.controller;

import com.resumegen.common.ApiResponse;
import com.resumegen.dto.WatermarkConfigVO;
import com.resumegen.security.UserContext;
import com.resumegen.service.WatermarkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 水印配置读取（R8-E）：前端导出链路 / 分享页据此决定是否叠加水印。
 */
@RestController
public class WatermarkConfigController {

    private final WatermarkService watermarkService;

    public WatermarkConfigController(WatermarkService watermarkService) {
        this.watermarkService = watermarkService;
    }

    /** 登录用户导出用配置（含盲水印负载）。 */
    @GetMapping("/api/watermark/config")
    public ApiResponse<WatermarkConfigVO> config(@RequestParam(required = false) String deviceId) {
        Long userId = UserContext.userId();
        return ApiResponse.ok(watermarkService.config(userId, deviceId));
    }

    /** 匿名公开配置（分享页可视水印用，不含盲水印负载）。 */
    @GetMapping("/api/public/watermark/config")
    public ApiResponse<WatermarkConfigVO> publicConfig(@RequestParam(required = false) String deviceId) {
        return ApiResponse.ok(watermarkService.config(null, deviceId));
    }
}
