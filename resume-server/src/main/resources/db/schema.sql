-- ============================================================
-- 简历生成器 数据库初始化脚本（MySQL 8.x，utf8mb4）
-- ============================================================
CREATE DATABASE IF NOT EXISTS resume_db
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE resume_db;

-- ---------- 1. 用户表 ----------
CREATE TABLE IF NOT EXISTS sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username      VARCHAR(50)  NOT NULL COMMENT '登录名',
  password      VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
  nickname      VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  email         VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  name          VARCHAR(50)  DEFAULT NULL COMMENT '真实姓名',
  title         VARCHAR(100) DEFAULT NULL COMMENT '求职意向/头衔',
  phone         VARCHAR(30)  DEFAULT NULL COMMENT '电话',
  location      VARCHAR(100) DEFAULT NULL COMMENT '所在地',
  website       VARCHAR(200) DEFAULT NULL COMMENT '个人主页/网站',
  avatar        VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
  summary       TEXT         DEFAULT NULL COMMENT '个人简介',
  role          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1正常 0禁用',
  token_version BIGINT       NOT NULL DEFAULT 0 COMMENT '登录令牌版本号(单会话踢下线用)',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_role (role)
) ENGINE=InnoDB COMMENT='用户表';

-- ---------- 2. 简历主表 ----------
CREATE TABLE IF NOT EXISTS resume (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id      BIGINT       NOT NULL COMMENT '所属用户(数据隔离)',
  title        VARCHAR(100) NOT NULL DEFAULT '未命名简历' COMMENT '简历名称',
  template_id  VARCHAR(20)  NOT NULL DEFAULT 'classic' COMMENT '模板: classic/modern/minimal',
  accent_color VARCHAR(20)  NOT NULL DEFAULT '#2563eb' COMMENT '主题色',
  version      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0删除',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_user_updated (user_id, updated_at)
) ENGINE=InnoDB COMMENT='简历主表';

-- ---------- 3. 个人信息表（1:1） ----------
CREATE TABLE IF NOT EXISTS resume_personal (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  resume_id  BIGINT       NOT NULL COMMENT '简历ID',
  user_id    BIGINT       NOT NULL COMMENT '冗余用户ID(隔离)',
  name       VARCHAR(50)  DEFAULT NULL,
  title      VARCHAR(100) DEFAULT NULL COMMENT '求职意向/头衔',
  email      VARCHAR(100) DEFAULT NULL,
  phone      VARCHAR(30)  DEFAULT NULL,
  location   VARCHAR(100) DEFAULT NULL,
  website    VARCHAR(200) DEFAULT NULL,
  avatar     VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
  summary    TEXT         COMMENT '个人简介',
  PRIMARY KEY (id),
  UNIQUE KEY uk_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='个人信息表';

-- ---------- 4. 教育经历表 ----------
CREATE TABLE IF NOT EXISTS resume_education (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  resume_id   BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  school      VARCHAR(100) DEFAULT NULL,
  degree      VARCHAR(50)  DEFAULT NULL,
  major       VARCHAR(100) DEFAULT NULL,
  start_date  VARCHAR(20)  DEFAULT NULL,
  end_date    VARCHAR(20)  DEFAULT NULL,
  gpa         VARCHAR(20)  DEFAULT NULL,
  description TEXT         DEFAULT NULL,
  sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='教育经历表';

-- ---------- 5. 工作/实习经历表（item_type 区分） ----------
CREATE TABLE IF NOT EXISTS resume_experience (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  resume_id   BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  item_type   VARCHAR(20)  NOT NULL DEFAULT 'EXPERIENCE' COMMENT 'EXPERIENCE/INTERNSHIP',
  company     VARCHAR(100) DEFAULT NULL,
  position    VARCHAR(100) DEFAULT NULL,
  start_date  VARCHAR(20)  DEFAULT NULL,
  end_date    VARCHAR(20)  DEFAULT NULL,
  current     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否至今',
  description TEXT         DEFAULT NULL,
  sort_order  INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='工作/实习经历表';

-- ---------- 6. 专业技能表 ----------
CREATE TABLE IF NOT EXISTS resume_skill (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  resume_id  BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  name       VARCHAR(100) DEFAULT NULL,
  level      INT          NOT NULL DEFAULT 3 COMMENT '熟练度 1-5',
  sort_order INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='专业技能表';

-- ---------- 7. 项目经历表 ----------
CREATE TABLE IF NOT EXISTS resume_project (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  resume_id   BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  name        VARCHAR(100) DEFAULT NULL,
  role        VARCHAR(100) DEFAULT NULL,
  start_date  VARCHAR(20)  DEFAULT NULL,
  end_date    VARCHAR(20)  DEFAULT NULL,
  description TEXT         DEFAULT NULL,
  link        VARCHAR(500) DEFAULT NULL,
  sort_order  INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='项目经历表';

-- ---------- 8. 证书荣誉表 ----------
CREATE TABLE IF NOT EXISTS resume_certificate (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  resume_id  BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  name       VARCHAR(100) DEFAULT NULL,
  issuer     VARCHAR(100) DEFAULT NULL,
  date       VARCHAR(20)  DEFAULT NULL,
  link       VARCHAR(500) DEFAULT NULL,
  sort_order INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='证书荣誉表';

-- ---------- 9. 语言能力表 ----------
CREATE TABLE IF NOT EXISTS resume_language (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  resume_id  BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  name       VARCHAR(50) DEFAULT NULL,
  level      VARCHAR(50) DEFAULT NULL,
  sort_order INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='语言能力表';

-- ---------- 10. 自定义板块表 ----------
CREATE TABLE IF NOT EXISTS resume_custom_section (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  resume_id  BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  title      VARCHAR(100) DEFAULT NULL,
  content    TEXT         DEFAULT NULL,
  sort_order INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='自定义板块表';

-- ---------- 11. 模块排序与显隐表 ----------
CREATE TABLE IF NOT EXISTS resume_section (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  resume_id    BIGINT      NOT NULL,
  user_id      BIGINT      NOT NULL,
  section_type VARCHAR(20) NOT NULL COMMENT 'personal/experience/internship/education/skills/projects/certificates/languages/custom',
  custom_id    VARCHAR(50) DEFAULT NULL COMMENT 'custom类型关联自定义板块',
  visible      TINYINT     NOT NULL DEFAULT 1,
  sort_order   INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='简历模块排序与显隐表';

-- ---------- 12. 简历分享表 ----------
CREATE TABLE IF NOT EXISTS resume_share (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id      BIGINT       NOT NULL COMMENT '所属用户(数据隔离)',
  resume_id    BIGINT       NOT NULL COMMENT '简历ID',
  share_key    VARCHAR(16)  NOT NULL COMMENT '分享key(唯一)',
  password     VARCHAR(64)  DEFAULT NULL COMMENT '访问密码(BCrypt,可空)',
  expire_at    DATETIME     DEFAULT NULL COMMENT 'NULL=永久',
  show_contact TINYINT      NOT NULL DEFAULT 0 COMMENT '是否展示联系方式',
  view_count   BIGINT       NOT NULL DEFAULT 0 COMMENT '访问次数',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1有效 0撤销',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_share_key (share_key),
  KEY idx_user (user_id),
  KEY idx_resume (resume_id)
) ENGINE=InnoDB COMMENT='简历分享表';

-- ---------- 13. 访问埋点原始日志 ----------
CREATE TABLE IF NOT EXISTS access_log (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       DEFAULT NULL COMMENT '登录用户(匿名为NULL)',
  device_id  VARCHAR(64)  DEFAULT NULL COMMENT '匿名设备ID(UV去重)',
  event_type VARCHAR(20)  NOT NULL DEFAULT 'page_view' COMMENT 'page_view/share_view/...',
  page       VARCHAR(100) DEFAULT NULL,
  resume_id  BIGINT       DEFAULT NULL COMMENT '关联简历',
  ts         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ts (ts),
  KEY idx_resume_ts (resume_id, ts),
  KEY idx_device_ts (device_id, ts)
) ENGINE=InnoDB COMMENT='访问埋点原始日志';

-- ---------- 14. 分钟级指标预聚合 ----------
CREATE TABLE IF NOT EXISTS stat_minute (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  stat_time  DATETIME    NOT NULL COMMENT '分钟粒度',
  metric     VARCHAR(32) NOT NULL COMMENT 'pv/uv/new_user/new_resume/...',
  dimension  VARCHAR(64) NOT NULL DEFAULT '',
  value      BIGINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_time_metric_dim (stat_time, metric, dimension),
  KEY idx_metric_time (metric, stat_time)
) ENGINE=InnoDB COMMENT='分钟级指标预聚合';

-- ---------- 15. 社区帖子 ----------
CREATE TABLE IF NOT EXISTS community_post (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       NOT NULL COMMENT '发布者(数据隔离)',
  resume_id     BIGINT       NOT NULL COMMENT '关联简历',
  title         VARCHAR(100) NOT NULL COMMENT '标题',
  summary       VARCHAR(500) DEFAULT NULL COMMENT '摘要',
  tags          VARCHAR(255) DEFAULT NULL COMMENT '标签(逗号分隔)',
  cover_url     VARCHAR(500) DEFAULT NULL COMMENT '封面',
  audit_status  TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审(pending) 1已上线(online) 2拒绝(rejected) 3人工复审(review) 4下架(offline)',
  audit_reason  VARCHAR(255) DEFAULT NULL COMMENT '拒绝/复审原因',
  like_count    BIGINT       NOT NULL DEFAULT 0,
  collect_count BIGINT       NOT NULL DEFAULT 0,
  comment_count BIGINT       NOT NULL DEFAULT 0,
  view_count    BIGINT       NOT NULL DEFAULT 0,
  report_count  INT          NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_status_time (audit_status, created_at),
  KEY idx_resume (resume_id)
) ENGINE=InnoDB COMMENT='社区帖子';

-- ---------- 16. 社区点赞 ----------
CREATE TABLE IF NOT EXISTS community_like (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  post_id    BIGINT   NOT NULL,
  user_id    BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB COMMENT='社区点赞';

-- ---------- 17. 社区收藏 ----------
CREATE TABLE IF NOT EXISTS community_collect (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  post_id    BIGINT   NOT NULL,
  user_id    BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_post_user (post_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='社区收藏';

-- ---------- 18. 社区评论 ----------
CREATE TABLE IF NOT EXISTS community_comment (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  post_id    BIGINT        NOT NULL,
  user_id    BIGINT        NOT NULL,
  parent_id  BIGINT        DEFAULT NULL COMMENT 'NULL=楼顶, 否则为回复的评论ID',
  content    VARCHAR(1000) NOT NULL,
  status     TINYINT       NOT NULL DEFAULT 1 COMMENT '1正常 0删除',
  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_post (post_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='社区评论';

-- ---------- 19. 社区举报 ----------
CREATE TABLE IF NOT EXISTS community_report (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  post_id    BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  reason     VARCHAR(255) DEFAULT NULL,
  status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0待处理 1已处理',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_post (post_id),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='社区举报';

-- ---------- 20. 简历模板表 ----------
CREATE TABLE IF NOT EXISTS template (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  code          VARCHAR(50)  NOT NULL COMMENT '模板唯一编码: classic/modern/minimal/用户自定义 u_xxx',
  name          VARCHAR(100) NOT NULL COMMENT '模板名称',
  type          VARCHAR(20)  NOT NULL DEFAULT 'official' COMMENT 'official官方/user用户',
  category      VARCHAR(50)  DEFAULT NULL COMMENT '风格分类',
  `schema`      JSON         DEFAULT NULL COMMENT '模板渲染Schema(JSON)',
  thumbnail     VARCHAR(500) DEFAULT NULL COMMENT '缩略图URL',
  owner_user_id BIGINT       DEFAULT NULL COMMENT '用户模板作者(数据隔离)',
  version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  schema_version INT         NOT NULL DEFAULT 1 COMMENT 'Schema版本',
  use_count     BIGINT       NOT NULL DEFAULT 0 COMMENT '被使用次数',
  view_count    BIGINT       NOT NULL DEFAULT 0 COMMENT '浏览数',
  audit_reason  VARCHAR(255) DEFAULT NULL COMMENT '审核拒绝/下架原因',
  published_at  DATETIME     DEFAULT NULL COMMENT '上架时间',
  sort_order    INT          NOT NULL DEFAULT 0 COMMENT '排序',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '0下架 1上架(online) 2待审(pending) 3草稿(draft)',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code),
  KEY idx_status_sort (status, sort_order),
  KEY idx_owner (owner_user_id)
) ENGINE=InnoDB COMMENT='简历模板';

-- ---------- 21. AI 知识库条目（RAG 用，R5/R6 共用） ----------
CREATE TABLE IF NOT EXISTS ai_kb_entry (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  kb_type    VARCHAR(32)  NOT NULL COMMENT 'interview_q/jd/resume_sample/writing_style/skill_prompt',
  code       VARCHAR(64)  DEFAULT NULL COMMENT '技能编码(prompt类)或文档唯一键',
  title      VARCHAR(200) DEFAULT NULL,
  content    TEXT         NOT NULL,
  metadata   JSON         DEFAULT NULL COMMENT '标签/岗位/难度/技术栈等',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_code (kb_type, code),
  KEY idx_type_status (kb_type, status)
) ENGINE=InnoDB COMMENT='AI知识库';

-- ---------- 22. 面试题集（R5） ----------
CREATE TABLE IF NOT EXISTS interview_set (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL,
  resume_id   BIGINT       NOT NULL,
  title       VARCHAR(100) DEFAULT NULL,
  target_role VARCHAR(100) DEFAULT NULL,
  questions   TEXT         COMMENT '结构化题库 JSON [{category,question,answer,tips}]',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='面试题集';

-- ---------- 23. AI 生成记录（R5/R6 共用） ----------
CREATE TABLE IF NOT EXISTS ai_generation_log (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  resume_id   BIGINT      DEFAULT NULL,
  task_type   VARCHAR(32) NOT NULL COMMENT 'rewrite/expand/suggest/interview',
  input_hash  VARCHAR(64) DEFAULT NULL,
  output      TEXT,
  token_usage INT         NOT NULL DEFAULT 0,
  status      TINYINT     NOT NULL DEFAULT 1,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='AI生成记录';

-- ---------- 24. 简历版本快照（R8-C 时光机） ----------
CREATE TABLE IF NOT EXISTS resume_snapshot (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL COMMENT '冗余隔离',
  resume_id   BIGINT       NOT NULL COMMENT '简历ID',
  version     INT          NOT NULL COMMENT '对应 resume.version',
  source      VARCHAR(32)  NOT NULL DEFAULT 'manual' COMMENT 'manual/ai_apply/rollback',
  content     JSON         NOT NULL COMMENT 'ResumeDTO 完整快照',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_resume (user_id, resume_id),
  UNIQUE KEY uk_resume_version (resume_id, version)
) ENGINE=InnoDB COMMENT='简历版本快照';