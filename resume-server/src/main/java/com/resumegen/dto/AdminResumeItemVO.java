package com.resumegen.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端简历项（携带所属用户信息）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminResumeItemVO extends ResumeListItemVO {

    private String userId;
    private String username;
}