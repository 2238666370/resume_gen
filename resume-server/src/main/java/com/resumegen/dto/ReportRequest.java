package com.resumegen.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 举报请求。
 */
@Data
public class ReportRequest {

    @Size(max = 255, message = "举报理由过长")
    private String reason;
}