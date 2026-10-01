package com.resumegen.dto;

import lombok.Data;

import java.util.List;

/**
 * 社区帖子公开信息（信息流/详情基类）。
 */
@Data
public class PostVO {

    private String id;
    private String title;
    private String summary;
    private List<String> tags;
    private String coverUrl;
    private String authorId;
    private String authorNickname;
    private String authorAvatar;
    private long likeCount;
    private long collectCount;
    private long commentCount;
    private long viewCount;
    private String createdAt;
}