package com.resumegen.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 简历详情返回：内容 + 服务端元信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResumeDetailVO extends ResumeDTO {

    private String id;
    private int version;
    private String updatedAt;
}