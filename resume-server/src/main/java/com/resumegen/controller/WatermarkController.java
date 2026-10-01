package com.resumegen.controller;

import com.resumegen.common.ApiResponse;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.security.UserContext;
import com.resumegen.service.WatermarkService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * 水印溯源（R8-E，管理端）：上传疑似盗用图片 → 解码盲水印 → 定位来源账号/设备。
 */
@RestController
@RequestMapping("/admin/watermark")
public class WatermarkController {

    private final WatermarkService watermarkService;

    public WatermarkController(WatermarkService watermarkService) {
        this.watermarkService = watermarkService;
    }

    @PostMapping("/decode")
    public ApiResponse<Map<String, Object>> decode(@RequestParam("file") MultipartFile file) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "请上传图片文件");
        }
        try {
            BufferedImage img = ImageIO.read(file.getInputStream());
            if (img == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "无法解析图片");
            }
            String payload = watermarkService.decodeLsb(img);
            return ApiResponse.ok(watermarkService.parsePayload(payload));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "图片解码失败");
        }
    }
}
