package com.resumegen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端审核请求：status 为目标审核状态（1上线/2拒绝/4下架）。
 */
@Data
public class AuditRequest {

    @NotNull(message = "请指定审核状态")
    private Integer status;

    @Size(max = 255, message = "审核理由过长")
    private String reason;
}