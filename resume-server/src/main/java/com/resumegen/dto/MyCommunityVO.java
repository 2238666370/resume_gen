package com.resumegen.dto;

import lombok.Data;

import java.util.List;

/**
 * 我的社区：我发布的 / 我点赞的 / 我收藏的。
 */
@Data
public class MyCommunityVO {

    private List<MyPostVO> posts;
    private List<PostVO> likes;
    private List<PostVO> collects;
}