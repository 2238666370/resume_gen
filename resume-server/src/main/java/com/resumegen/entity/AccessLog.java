package com.resumegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 访问埋点原始日志（PV/UV 数据源）。
 */
@Data
@TableName("access_log")
public class AccessLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 登录用户（匿名访问为 null）。 */
    private Long userId;
    /** 匿名设备 ID（UV 去重）。 */
    private String deviceId;
    private String eventType;
    private String page;
    private Long resumeId;
    private LocalDateTime ts;
}