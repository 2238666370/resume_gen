package com.resumegen.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShareCreateRequest {

    @NotNull(message = "简历ID不能为空")
    private Long resumeId;

    /** 有效期天数：0 或 null 表示永久。 */
    private Integer expireDays;

    /** 可选访问密码（明文，服务端 BCrypt 加密存储）。 */
    private String password;

    /** 是否展示联系方式（手机/邮箱）。 */
    private Boolean showContact = false;
}