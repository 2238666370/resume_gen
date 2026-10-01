package com.resumegen.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 简历评分结果（ATS 风格 + JD 匹配度 + 可解释建议）。
 */
@Data
public class ResumeScoreVO {

    private Integer totalScore;
    private Integer matchedPercent;
    private List<Dimension> dimensions = new ArrayList<>();
    private List<String> skillHits = new ArrayList<>();
    private List<String> missingSkills = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();

    @Data
    public static class Dimension {
        private String key;
        private String name;
        private Integer score;
        private String comment;
    }
}
