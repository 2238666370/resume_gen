package com.resumegen.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 页面访问埋点请求。
 */
@Data
public class TrackRequest {

    private String eventType;

    @Size(max = 100, message = "页面路径过长")
    private String page;

    private Long resumeId;

    @Size(max = 64, message = "设备ID过长")
    private String deviceId;
}