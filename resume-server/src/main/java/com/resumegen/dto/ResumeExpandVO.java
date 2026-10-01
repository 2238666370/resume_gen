package com.resumegen.dto;

import lombok.Data;

/**
 * STAR 扩写结果。
 */
@Data
public class ResumeExpandVO {

    private String original;
    private String expanded;
}