package com.resumegen.dto;

import lombok.Data;

/**
 * 润色结果。
 */
@Data
public class ResumeRewriteVO {

    private String original;
    private String revised;
}