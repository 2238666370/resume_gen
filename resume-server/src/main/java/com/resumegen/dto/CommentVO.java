package com.resumegen.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 社区评论（含楼中楼子评论）。
 */
@Data
public class CommentVO {

    private String id;
    private String postId;
    private String userId;
    private String userNickname;
    private String userAvatar;
    private String parentId;
    private String content;
    private String createdAt;
    private List<CommentVO> children = new ArrayList<>();
}