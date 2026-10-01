package com.resumegen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 简历内容聚合 DTO，与前端 ResumeData 对齐（camelCase）。
 * 作为存储抽象（JSON/MySQL）与接口的统一定义。
 */
@Data
public class ResumeDTO {

    /** 日期格式：yyyy.MM / yyyy-MM（可带日），允许为空（如"至今"）。 */
    public static final String DATE_PATTERN = "^$|^\\d{4}[-.]\\d{1,2}([-.]\\d{1,2})?$";
    /** 拒绝危险 HTML/脚本片段（script/iframe/事件属性/javascript: 等）。 */
    public static final String NO_XSS_PATTERN =
            "(?i)^(?![\\s\\S]*(<script|</script|<iframe|<img|<svg|javascript:|vbscript:"
                    + "|on(load|error|click|mouseover|focus|blur|change|input|submit)\\s*=))[\\s\\S]*$";

    private String title = "未命名简历";
    private String templateId = "classic";

    @Pattern(regexp = "^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$", message = "主题色需为 #RGB 或 #RRGGBB 格式")
    private String accentColor = "#2563eb";

    @Valid
    private Personal personal = new Personal();
    @Valid
    private List<EducationItem> education = new ArrayList<>();
    @Valid
    private List<ExperienceItem> experience = new ArrayList<>();
    @Valid
    private List<ExperienceItem> internship = new ArrayList<>();
    private List<SkillItem> skills = new ArrayList<>();
    @Valid
    private List<ProjectItem> projects = new ArrayList<>();
    @Valid
    private List<CertificateItem> certificates = new ArrayList<>();
    private List<LanguageItem> languages = new ArrayList<>();
    @Valid
    private List<CustomSection> customSections = new ArrayList<>();
    private List<Section> sections = new ArrayList<>();

    @Data
    public static class Personal {
        private String name;
        private String title;
        private String email;
        private String phone;
        private String location;
        private String website;
        private String avatar;
        @Pattern(regexp = NO_XSS_PATTERN, message = "个人简介包含非法内容")
        private String summary;
    }

    @Data
    public static class EducationItem {
        private String id;
        private String school;
        private String degree;
        private String major;
        @Pattern(regexp = DATE_PATTERN, message = "开始时间格式不正确")
        private String startDate;
        @Pattern(regexp = DATE_PATTERN, message = "结束时间格式不正确")
        private String endDate;
        private String gpa;
        @Pattern(regexp = NO_XSS_PATTERN, message = "描述包含非法内容")
        private String description;
    }

    @Data
    public static class ExperienceItem {
        private String id;
        private String company;
        private String position;
        @Pattern(regexp = DATE_PATTERN, message = "开始时间格式不正确")
        private String startDate;
        @Pattern(regexp = DATE_PATTERN, message = "结束时间格式不正确")
        private String endDate;
        private boolean current;
        @Pattern(regexp = NO_XSS_PATTERN, message = "描述包含非法内容")
        private String description;
    }

    @Data
    public static class SkillItem {
        private String id;
        private String name;
        private int level;
    }

    @Data
    public static class ProjectItem {
        private String id;
        private String name;
        private String role;
        @Pattern(regexp = DATE_PATTERN, message = "开始时间格式不正确")
        private String startDate;
        @Pattern(regexp = DATE_PATTERN, message = "结束时间格式不正确")
        private String endDate;
        @Pattern(regexp = NO_XSS_PATTERN, message = "描述包含非法内容")
        private String description;
        private String link;
    }

    @Data
    public static class CertificateItem {
        private String id;
        private String name;
        private String issuer;
        @Pattern(regexp = DATE_PATTERN, message = "日期格式不正确")
        private String date;
        private String link;
    }

    @Data
    public static class LanguageItem {
        private String id;
        private String name;
        private String level;
    }

    @Data
    public static class CustomSection {
        private String id;
        private String title;
        @Pattern(regexp = NO_XSS_PATTERN, message = "内容包含非法内容")
        private String content;
    }

    @Data
    public static class Section {
        private String id;
        private String type;
        private String customId;
        private boolean visible;
        private int order;
    }
}