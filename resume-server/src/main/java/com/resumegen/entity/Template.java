package com.resumegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历模板元信息（表驱动）。`schema` 为渲染 Schema（JSON 字符串），
 * 官方模板与用户自定义模板均以 schema 驱动渲染（前端 SchemaRenderer 消费）。
 */
@Data
@TableName("template")
public class Template {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String type;
    private String category;
    /** 渲染 Schema（JSON 字符串），可为 null（前端回退内置预设）。`schema` 为 MySQL 保留字，需反引号。 */
    @TableField("`schema`")
    private String schema;
    private String thumbnail;
    private Long ownerUserId;
    private Integer version;
    private Integer schemaVersion;
    private Long useCount;
    private Long viewCount;
    private String auditReason;
    private LocalDateTime publishedAt;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}