package com.resumegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历版本快照（R8-C 时光机）。
 */
@Data
@TableName("resume_snapshot")
public class ResumeSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long resumeId;
    private Integer version;
    /** manual / ai_apply / rollback。 */
    private String source;
    /** ResumeDTO 完整快照 JSON。 */
    private String content;
    private LocalDateTime createdAt;
}
