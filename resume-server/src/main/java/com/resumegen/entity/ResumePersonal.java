package com.resumegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("resume_personal")
public class ResumePersonal {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private Long userId;
    private String name;
    private String title;
    private String email;
    private String phone;
    private String location;
    private String website;
    private String avatar;
    private String summary;
}