package com.resumegen.dto;

import lombok.Data;

/**
 * 简历版本快照列表/详情项。
 */
@Data
public class ResumeSnapshotVO {

    private String id;
    private String resumeId;
    private int version;
    private String source;
    private String createdAt;
    /** 详情接口才填充完整内容。 */
    private ResumeDTO content;
}
