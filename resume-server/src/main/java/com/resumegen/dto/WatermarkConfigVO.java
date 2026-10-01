package com.resumegen.dto;

import lombok.Data;

/**
 * 水印配置（供前端导出/分享页读取，含已生成的实际文案与盲水印负载）。
 */
@Data
public class WatermarkConfigVO {

    /** 总开关（控制可视水印）。 */
    private boolean enabled;
    /** 可视水印实际文案（占位符已替换）。 */
    private String visibleText;
    private double visibleOpacity;
    private String visibleDensity;
    /** 盲水印开关。 */
    private boolean blindEnabled;
    /** 盲水印负载（登录导出用；匿名接口不返回）。 */
    private String blindPayload;
}
