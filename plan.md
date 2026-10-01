# 简历生成器 — 前后端分离改造方案（plan）

> 目标：将当前纯前端（React + Vite + Zustand + localStorage）的简历填写项目，拆分为「前端 + Java/Spring Boot 后端」的分离架构。
> 本文件为规划文档，仅描述架构与改造步骤，不包含业务代码实现。

---

## 1. 现状概述

当前项目（`resume_gen`）是一个纯前端应用，具备以下特征：

- **前端栈**：React 19 + TypeScript + Vite 5 + Tailwind CSS 3 + Zustand（`persist` 到 `localStorage`）
- **编辑器**：左侧模块化表单（个人信息 / 工作经历 / 实习经历 / 教育经历 / 技能 / 项目 / 证书 / 语言 / 自定义板块）
- **预览**：3 套模板（Classic / Modern / Minimal）实时渲染
- **导出**：PNG（html2canvas）、PDF（Electron `printToPDF` / 浏览器原生打印）、JSON（保存/导入备份）
- **数据模型**：单份完整 `ResumeData`（见 `src/types/resume.ts`），整体作为一个对象存储
- **运行形态**：Electron 桌面版 + 浏览器本地静态服务（`local-server.cjs`）

**核心问题**：数据仅存在本地（localStorage / JSON 文件），无用户体系、无服务端存储、无多用户数据隔离、无管理能力。

---

## 2. 目标架构

采用前后端完全分离 + 多端（用户端 Web / 管理端 Web / 独立 API 服务）的架构：

```
┌──────────────┐   ┌──────────────┐
│ 用户端 Web    │   │ 管理端 Web    │
│ (resume-ui)  │   │ (admin-ui)   │
└──────┬───────┘   └──────┬───────┘
       │  HTTP/JSON        │  HTTP/JSON（管理员）
       ▼                   ▼
┌──────────────────────────────────────┐
│         后端 resume-server            │
│  Spring Boot 3 + JDK 17              │
│  ├─ Controller 层（REST API）         │
│  ├─ Service 层（业务 + 隔离校验）       │
│  ├─ Repository 层（存储抽象）          │
│  │    ├─ JsonResumeRepository        │
│  │    └─ MysqlResumeRepository       │
│  ├─ 认证鉴权（JWT）                   │
│  └─ 缓存（Redis）                     │
└──────┬──────────────┬────────────────┘
       │              │
   ┌───▼────┐   ┌─────▼─────┐
   │ MySQL  │   │  JSON     │
   │ (关系)  │   │ (文件/JSON列)│
   └────────┘   └───────────┘
          ┌──────┐
          │ Redis│（缓存）
          └──────┘
```

---

## 3. 技术选型

| 层 | 技术 | 版本/说明 |
|----|------|-----------|
| 后端框架 | Spring Boot | 3.x（Java 17+） |
| ORM | MyBatis-Plus | 简化 CRUD、分页、逻辑删除 |
| 数据库 | MySQL | 8.x（utf8mb4） |
| 缓存 | Redis | 5+（Spring Data Redis） |
| 认证 | JWT | jjwt / java-jwt，无状态会话 |
| 参数校验 | Spring Validation | `jakarta.validation` |
| 接口文档 | springdoc-openapi | Swagger 3 |
| 前端（用户端） | React + Vite + TS + Tailwind + Zustand | 复用现有 UI，新增 axios/router |
| 前端（管理端） | React + Vite + TS + Ant Design | 独立工程 |

---

## 4. 后端设计

### 4.1 模块划分（Maven 多模块 / 单模块分包均可）

```
resume-server/
├── src/main/java/com/resumegen/
│   ├── config/          # WebConfig、RedisConfig、JacksonConfig、ThreadPool
│   ├── controller/      # 用户端 + 管理端 REST 控制器
│   ├── service/         # 业务接口 + 实现
│   ├── repository/      # 存储抽象接口 + Json/Mysql 双实现
│   ├── entity/          # MyBatis-Plus 实体（MySQL 模式）
│   ├── mapper/          # MyBatis Mapper 接口
│   ├── dto/             # 请求/响应对象
│   ├── vo/              # 视图对象
│   ├── security/        # JWT 过滤器、拦截器、用户上下文
│   ├── common/          # 统一响应、异常、错误码、分页
│   └── admin/           # 管理后台专属服务（统计、用户管理）
└── src/main/resources/
    ├── application.yml
    ├── application-dev.yml
    ├── application-prod.yml
    └── db/schema.sql    # 建表脚本
```

### 4.2 领域模型与表映射

现有 `ResumeData` 直接映射为「1 个主表 + N 个明细表」，全部挂 `user_id` 实现隔离。

| 前端类型 | 关系表 | 关系 |
|----------|--------|------|
| ResumeData 顶层 + PersonalInfo | `resume` + `resume_personal` | 1 用户 : N 简历；1 简历 : 1 个人信息 |
| EducationItem[] | `resume_education` | 1 简历 : N |
| ExperienceItem[] / InternshipItem[] | `resume_experience`（`item_type` 区分） | 1 简历 : N |
| SkillItem[] | `resume_skill` | 1 简历 : N |
| ProjectItem[] | `resume_project` | 1 简历 : N |
| CertificateItem[] | `resume_certificate` | 1 简历 : N |
| LanguageItem[] | `resume_language` | 1 简历 : N |
| CustomSection[] | `resume_custom_section` | 1 简历 : N |
| ResumeSection[]（排序/显示） | `resume_section` | 1 简历 : N |

> 说明：`experience` 与 `internship` 结构一致，合并为一张 `resume_experience` 表，用 `item_type` 列区分（`EXPERIENCE` / `INTERNSHIP`）。

### 4.3 建表 SQL

```sql
-- ============================================================
-- 简历生成器 数据库初始化脚本（MySQL 8.x，utf8mb4）
-- ============================================================
CREATE DATABASE IF NOT EXISTS resume_db
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE resume_db;

-- ---------- 1. 用户表 ----------
CREATE TABLE sys_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username    VARCHAR(50)  NOT NULL COMMENT '登录名',
  password    VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
  nickname    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  email       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  role        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1正常 0禁用',
  token_version BIGINT     NOT NULL DEFAULT 0 COMMENT '登录令牌版本号(单会话踢下线用)',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_role (role)
) ENGINE=InnoDB COMMENT='用户表';

-- ---------- 2. 简历主表 ----------
CREATE TABLE resume (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT      NOT NULL COMMENT '所属用户(数据隔离)',
  title       VARCHAR(100) NOT NULL DEFAULT '未命名简历' COMMENT '简历名称',
  template_id VARCHAR(20)  NOT NULL DEFAULT 'classic' COMMENT '模板: classic/modern/minimal',
  accent_color VARCHAR(20) NOT NULL DEFAULT '#2563eb' COMMENT '主题色',
  version     INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0删除',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_user_updated (user_id, updated_at)
) ENGINE=InnoDB COMMENT='简历主表';

-- ---------- 3. 个人信息表（1:1） ----------
CREATE TABLE resume_personal (
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
CREATE TABLE resume_education (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  resume_id  BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  school     VARCHAR(100) DEFAULT NULL,
  degree     VARCHAR(50)  DEFAULT NULL,
  major      VARCHAR(100) DEFAULT NULL,
  start_date VARCHAR(20)  DEFAULT NULL,
  end_date   VARCHAR(20)  DEFAULT NULL,
  gpa        VARCHAR(20)  DEFAULT NULL,
  description TEXT        DEFAULT NULL,
  sort_order INT          NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='教育经历表';

-- ---------- 5. 工作/实习经历表（item_type 区分） ----------
CREATE TABLE resume_experience (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  resume_id  BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  item_type  VARCHAR(20)  NOT NULL DEFAULT 'EXPERIENCE' COMMENT 'EXPERIENCE/INTERNSHIP',
  company    VARCHAR(100) DEFAULT NULL,
  position   VARCHAR(100) DEFAULT NULL,
  start_date VARCHAR(20)  DEFAULT NULL,
  end_date   VARCHAR(20)  DEFAULT NULL,
  current    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否至今',
  description TEXT        DEFAULT NULL,
  sort_order INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='工作/实习经历表';

-- ---------- 6. 专业技能表 ----------
CREATE TABLE resume_skill (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  resume_id  BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  name       VARCHAR(100) DEFAULT NULL,
  level      INT         NOT NULL DEFAULT 3 COMMENT '熟练度 1-5',
  sort_order INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='专业技能表';

-- ---------- 7. 项目经历表 ----------
CREATE TABLE resume_project (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  resume_id  BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  name       VARCHAR(100) DEFAULT NULL,
  role       VARCHAR(100) DEFAULT NULL,
  start_date VARCHAR(20)  DEFAULT NULL,
  end_date   VARCHAR(20)  DEFAULT NULL,
  description TEXT        DEFAULT NULL,
  link       VARCHAR(500) DEFAULT NULL,
  sort_order INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='项目经历表';

-- ---------- 8. 证书荣誉表 ----------
CREATE TABLE resume_certificate (
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
CREATE TABLE resume_language (
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
CREATE TABLE resume_custom_section (
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
CREATE TABLE resume_section (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  resume_id     BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  section_type  VARCHAR(20) NOT NULL COMMENT 'personal/experience/internship/education/skills/projects/certificates/languages/custom',
  custom_id     VARCHAR(50) DEFAULT NULL COMMENT 'custom类型关联自定义板块',
  visible       TINYINT     NOT NULL DEFAULT 1,
  sort_order    INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='简历模块排序与显隐表';
```

> 隔离设计：所有明细表冗余 `user_id` 并建索引，查询时强制 `WHERE user_id = ?`，避免跨用户越权。也可改为仅挂 `resume_id`、经 `resume.user_id` 校验，二选一；本方案取「冗余 user_id」以简化并加速隔离查询。
> 并发设计：`resume.version` 用于乐观锁，避免多端同事保存互相覆盖。

---

### 4.4 yml 配置

`application.yml`（示例，含存储切换、Redis、JWT、数据源）：

```yaml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  application:
    name: resume-server
  profiles:
    active: dev

  # 数据源（MySQL 模式时启用）
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3306/resume_db?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: root
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  # Redis 缓存（仅 resume.cache.type=redis 时需要，type=none 时可整体删除本段）
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password:
      database: 0
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 16
          max-idle: 8

  # 上传/请求体大小上限（含头像 JSON，预留富文本）
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

# MyBatis-Plus
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      logic-delete-field: status

# ------- 业务配置 -------
resume:
  storage:
    type: mysql          # 存储后端: mysql | json
    json-path: ./data/resumes  # json 模式下的数据目录
    json-in-mysql: false        # 若为 true，json 模式也存 MySQL 的 JSON 列
  cache:
    type: redis          # 缓存后端: redis | none（none 表示不使用 Redis，可完全脱 Redis 运行）
    ttl-seconds: 3600    # 简历详情缓存时长
    prefix: resume:
  cors:
    allowed-origins: "http://localhost:5173,http://localhost:3000"

# JWT
jwt:
  secret: "please-change-me-to-a-long-random-secret-at-least-32-chars"
  expire-seconds: 86400   # 令牌有效期（秒）
  header: Authorization
  token-prefix: "Bearer "
```

`application-dev.yml` / `application-prod.yml` 通过 Spring Profile 覆盖：开发可用 `mysql`/`json` + `cache.type=none`（免 Redis），生产用 `mysql` + `cache.type=redis`。当 `cache.type=none` 时，`spring.data.redis` 段可整体删除，且运行时不依赖 Redis。

---

### 4.5 存储层抽象（JSON / MySQL 双实现）

定义统一仓储接口 `ResumeRepository`（面向 `ResumeData` / `ResumeDetailVO` 聚合操作）：

```text
ResumeRepository
 ├─ JsonResumeRepository      # 将整份 ResumeData 序列化为 JSON
 │     · 落盘：JSON 文件（json-path 目录，按 user_id 分目录隔离）
 │     · 或：MySQL 单表 JSON 列（resume_doc 表）当 json-in-mysql=true
 └─ MysqlResumeRepository     # 按 4.3 节 11 张关系表归一化读写（MyBatis-Plus）
```

- 通过 `resume.storage.type` 决定注入哪个实现（`@ConditionalOnProperty`）。
- 对外（Service/Controller）只依赖抽象接口，业务层无感知。
- 聚合读写在一个事务内完成（MySQL 模式），保证子表一致性。
- JSON 模式下 `user_id` 通过文件目录 / 文档字段实现同等级别的隔离。

### 4.6 Redis 缓存策略

> **缓存可选**：Redis 是否启用由 `resume.cache.type`（`redis | none`）控制。`none` 时不装配缓存层（`@ConditionalOnProperty`），读写直达存储，应用可在无 Redis 环境运行。以下策略仅在 `type=redis` 时生效。

| 场景 | Key | TTL | 失效时机 |
|------|-----|-----|----------|
| 简历详情聚合 | `resume:detail:{resumeId}` | 3600s | 任何写操作（PUT/删明细）删除该 key |
| 简历列表 | `resume:list:{userId}` | 300s | 新增/删除简历时删除 |
| 用户信息 | `resume:user:{userId}` | 3600s | 资料修改时删除 |
| 活跃会话版本（单点登录，DB 兜底） | `resume:login:ver:{userId}` | 与令牌同长 | 登录覆盖；登出/踢下线删除；未命中回源 DB |

- 采用 **Cache-Aside**（旁路缓存）模式：读缓存未命中 → 查库 → 回填缓存。
- 明细子表变更 → 使失效对应 `resume:detail:{resumeId}`，保证一致性。
- 缓存仅做加速，不承载权限判定；权限校验仍在 Service 层强制 `user_id` 过滤。

### 4.7 用户数据隔离

1. **认证**：JWT 承载 `userId`，过滤器解析后写入 `UserContext`（ThreadLocal）。
2. **隔离规则**：
   - 所有简历读写接口，从 Token 取 `userId`，强制作为查询/写入条件；
   - 资源归属校验：操作 `resumeId` 前校验 `resume.user_id == 当前 userId`，否则返回 403；
   - 明细表冗余 `user_id`，查询一律 `WHERE user_id = ?`；
   - 删除/更新采用「当前用户拥有」语义，杜绝横向越权（IDOR）。
3. **管理员例外**：`ADMIN` 角色可跨用户查看，通过独立 `/admin/**` 接口 + `@PreAuthorize("hasRole('ADMIN')")` 控制，不在用户端接口混用。

### 4.8 单点登录（单会话，踢下线）

**需求**：同一用户同一时间仅允许一个有效登录；新登录成功后，该用户此前的登录立即失效（被踢下线）。

**实现方案（令牌版本号 `token_version`，Redis 可选）**：

1. **数据模型**：`sys_user` 表新增 `token_version BIGINT`（默认 0），作为「当前有效会话代际」，是唯一权威源。
2. **签发**：登录成功原子执行 `UPDATE sys_user SET token_version = token_version + 1 WHERE id = ?`，将新版本号写入 JWT 自定义 claim `ver`；启用 Redis（`cache.type=redis`）时同步写 `resume:login:ver:{userId} = ver`。
3. **校验**：每个请求解析 JWT 得到 `userId` + `ver`，与「当前有效版本」比对：
   - Redis 命中 → 比 Redis（加速）；
   - Redis 未命中 / `cache.type=none` → 回源 DB 比 `token_version`（兜底，保证无 Redis 也能踢下线）。
   - 不一致 → 判定为旧会话，返回 `401`（提示「账号已在其他设备登录」）。
4. **踢下线**：新登录使 `ver` 递增，旧 Token 的 `ver` 落后立即失效 —— 实现「新登录踢掉老登录」，无需黑名单。
5. **登出**：`token_version + 1`（当前 Token 失效）+ 删除 `resume:login:ver:{userId}`。

**要点**：
- Redis 仅作加速，**DB 的 `token_version` 才是权威源**，故 `cache.type=none` 下功能仍完整。
- 登录/登出的递增必须原子（`UPDATE ... SET token_version = token_version + 1`），避免并发登录导致 version 回退。
- 单会话按 `user_id` 全局生效（用户端与管理端若共用同一账号也互相踢线；如需分离另建账号体系，本次不涉及）。
- JWT claim 结构新增：`sub`(userId)、`role`、`ver`(token_version)。

### 4.9 REST API 设计（摘要）

**认证**
```
POST /api/auth/register          注册
POST /api/auth/login             登录（返回 JWT）
POST /api/auth/logout            登出
GET  /api/auth/me                当前用户信息
```

**简历（当前用户）**
```
GET    /api/resumes              简历列表（分页）
POST   /api/resumes              新建简历
GET    /api/resumes/{id}         简历详情（聚合，含缓存）
PUT    /api/resumes/{id}         整体保存简历
PATCH  /api/resumes/{id}         局部更新（模板/配色/单独模块）
DELETE /api/resumes/{id}         删除简历
GET    /api/resumes/{id}/export.json   导出 JSON 备份
POST   /api/resumes/import       导入 JSON 恢复
```

**管理后台（ADMIN）**
```
GET /admin/users                 用户列表/搜索/分页
PUT /admin/users/{id}/status     启用/禁用用户
GET /admin/resumes               全量简历列表（跨用户）
GET /admin/stats                 统计（用户数/简历数/今日新增等）
DELETE /admin/resumes/{id}       删除任意简历
```

**通用**：统一响应体 `{ code, message, data }`；全局异常处理（业务异常、参数校验异常、鉴权异常）。

> 本节为接口清单，「契约字段」「校验规则」「响应示例」见第 5 节。

---

## 5. 前后端输入 / 输出契约（接口约束）

本节作为前后端联调的唯一契约基准：字段命名、类型、必填、长度等以本节为准，前后端（TS 类型 / Java DTO）必须对齐。

### 5.1 通用约定

**统一响应体**

```json
{
  "code": 0,
  "message": "success",
  "data": { }
}
```

- `code`：0 表示成功，非 0 表示失败。
- `data`：成功时的业务数据；失败时为 `null`。

**错误码**

| code | 含义 | 触发场景 |
|------|------|----------|
| 0 | 成功 | 正常返回 |
| 400 | 参数错误 | 字段校验失败、类型不符、越界 |
| 401 | 未认证 | 缺失/过期/非法 Token |
| 403 | 无权限 | 访问他人资源、非管理员访问 /admin |
| 404 | 资源不存在 | 简历/用户不存在或已删除 |
| 409 | 冲突 | 用户名已存在、版本冲突（乐观锁） |
| 500 | 服务器错误 | 未捕获异常 |

**统一头部**

| 请求头 | 值 | 说明 |
|--------|-----|------|
| `Authorization` | `Bearer <jwt>` | 除注册/登录外的所有接口必带 |
| `Content-Type` | `application/json; charset=utf-8` | 所有 JSON 请求体 |

**命名与类型约定**

- 字段一律 **camelCase**（与现有 `resume.ts` 一致），Jackson 默认序列化；数据库为 snake_case，由 MyBatis-Plus `map-underscore-to-camel-case` 自动转换。
- 日期/时间段字段为 **字符串**：日期用 `yyyy-MM-dd`，月份用 `yyyy-MM`（保持与前端一致，不引入时间戳解析歧义）。
- `id` 统一为字符串（前端生成）；后端 MySQL 自增 `BIGINT` 在返回时转 `String`（防 JS 精度丢失）；导入旧 JSON 时保留其 `id`。
- 空数组与缺失等价：明细 `null` 一律按 `[]` 处理；字符串可空。
- 颜色 `accentColor` 必须匹配 `#RRGGBB`（十六进制）。
- 富文本（`description` / `content` / `summary`）仅存**纯文本 + `**加粗**` 语法**，禁止存放 HTML 标签（防 XSS），渲染由前端 `textRenderer` 完成。

**分页结构**（`GET /api/resumes`、`/admin/*`）

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "page": 1,
    "size": 10
  }
}
```

- `page` 从 1 开始，`size` 默认 10、上限 100。

### 5.2 核心数据契约：ResumeDTO（与 `ResumeData` 对齐）

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `title` | string | 否 | ≤100 | 简历名称（**新增**，列表展示用） |
| `templateId` | enum | 否 | classic/modern/minimal | 模板 |
| `accentColor` | string | 否 | `#RRGGBB` | 主题色 |
| `personal` | object | 否 | 见 PersonalDTO | 个人信息 |
| `education` | array | 否 | ≤100 | 教育经历 |
| `experience` | array | 否 | ≤100 | 工作经历 |
| `internship` | array | 否 | ≤100 | 实习经历 |
| `skills` | array | 否 | ≤100 | 技能 |
| `projects` | array | 否 | ≤100 | 项目 |
| `certificates` | array | 否 | ≤100 | 证书 |
| `languages` | array | 否 | ≤100 | 语言 |
| `customSections` | array | 否 | ≤100 | 自定义板块 |
| `sections` | array | 否 | ≤50 | 模块排序与显隐 |

**PersonalDTO**

| 字段 | 类型 | 约束 |
|------|------|------|
| `name` | string | ≤50 |
| `title` | string | ≤100 |
| `email` | string | email 格式 |
| `phone` | string | ≤30 |
| `location` | string | ≤100 |
| `website` | string | URL 格式、≤200 |
| `avatar` | string | http(s) URL、≤500 |
| `summary` | string | 纯文本 |

**列表项 DTO 字段约束**（所有明细数组元素）

| DTO | 字段（类型） |
|-----|-------------|
| EducationDTO | `school` ≤100、`degree` ≤50、`major` ≤100、`startDate`/`endDate` 日期、`gpa` ≤20、`description` 文本 |
| ExperienceDTO（工作/实习共用） | `company` ≤100、`position` ≤100、`startDate`/`endDate` 日期、`current` bool、`description` 文本 |
| SkillDTO | `name` ≤100、`level` int 1–5 |
| ProjectDTO | `name` ≤100、`role` ≤100、`startDate`/`endDate` 日期、`description` 文本、`link` URL ≤500 |
| CertificateDTO | `name` ≤100、`issuer` ≤100、`date`、`link` URL ≤500 |
| LanguageDTO | `name` ≤50、`level` ≤50 |
| CustomSectionDTO | `title` ≤100、`content` 文本 |
| SectionDTO | `type` enum（9 种）、`customId` string（仅 custom 用）、`visible` bool、`order` int |

### 5.3 接口契约明细

**① 注册 `POST /api/auth/register`**

请求：
```json
{ "username": "alice", "password": "Passw0rd!", "email": "a@x.com" }
```
约束：`username` 3–50（字母/数字/下划线）、`password` 6–64、`email` 可选但需合法。
响应（成功）：`code=0`，`data` 返回 `{ "id":"1", "username":"alice" }`。
异常：用户名存在 → `409`。

**② 登录 `POST /api/auth/login`**

请求：
```json
{ "username": "alice", "password": "Passw0rd!" }
```
响应：
```json
{ "code": 0, "message": "success",
  "data": { "token": "<jwt>", "expiresIn": 86400,
            "user": { "id":"1", "username":"alice", "role":"USER", "nickname":"Alice" } } }
```
异常：账号/密码错误 → `400`；账户被禁用 → `403`。
> 单会话：登录成功后，该用户此前签发的所有 Token 立即失效（旧登录被踢下线）；被踢的旧 Token 后续请求返回 `401`。

**③ 当前用户 `GET /api/auth/me`**
响应 `data`：`{ "id","username","role","nickname","email","createdAt" }`。
> 永不返回 `password` 字段（脱敏）。

**④ 简历列表 `GET /api/resumes?page=1&size=10&keyword=xx`**
响应 `data`（分页）：`records` 元素 `{ "id", "title", "templateId", "updatedAt" }`（不含明细，精简列表）。

**⑤ 新建简历 `POST /api/resumes`**
请求：
```json
{ "title": "我的简历" }
```
响应：`data` 返回 `{ "id": "123" }`，服务端初始化默认 `ResumeData`（含默认 sections）。

**⑥ 简历详情 `GET /api/resumes/{id}`**
响应：`data` 为完整 `ResumeDTO`（见 5.2）。
异常：不存在或非本人 → `404`（不暴露他人资源存在性，统一 404）。

**⑦ 整体保存 `PUT /api/resumes/{id}`**
请求：完整 `ResumeDTO`（前端 put 整个 store）。
响应：`data` 返回新 `version` 及 `updatedAt`。
幂等：全量覆盖语义。

**⑧ 局部更新 `PATCH /api/resumes/{id}`**
请求：`ResumeDTO` 的**部分字段**（如仅 `{ "templateId": "modern" }` 或仅 `{ "skills":[...] }`）。
响应：同 ⑦。

**⑨ 删除 `DELETE /api/resumes/{id}`**
响应：`code=0`（逻辑删除）。重复删除幂等返回成功或 `404`。

**⑩ 导出 `GET /api/resumes/{id}/export.json`**
响应：`Content-Type: application/json`，`Content-Disposition: attachment; filename=xxx.json`，内容为纯 `ResumeData`（不带 `code/message` 包装，便于离线导入）。

**⑪ 导入 `POST /api/resumes/import`**
请求：`multipart/form-data` 或 `application/json`（原始 `ResumeData`）。
响应：`data` 返回新简历 `{ "id", "title" }`。

**⑫ 管理端（ADMIN）**：请求/响应结构与用户端一致，仅鉴权为 `ADMIN`；`/admin/resumes` 返回项额外携带 `username` / `userId` 字段。

---

## 6. 管理后台设计

独立前端工程 `admin-ui`，复用后端 `/admin/**` 接口，功能：

- **用户管理**：用户列表、搜索、启用/禁用、角色分配。
- **简历管理**：查看所有用户的简历、预览、删除、导出。
- **数据统计**：Dashboard 看板（用户数、简历数、模板使用分布、每日新增曲线）。
- **系统配置**：存储模式只读展示、缓存状态。

---

## 7. 前端改造

### 7.1 用户端 `resume-ui`

- 复用现有编辑器、3 套模板、导出逻辑（PNG/PDF 纯前端不变）。
- **替换数据层**：Zustand `persist` → 后端 API。
  - 移除 `localStorage` 持久化，改为登录态 + 服务端读写。
  - `resumeStore` 的增删改动作改为调用 `api`，成功后刷新本地 store。
- **新增**：`axios`（拦截器注入 JWT）、`react-router`。
- **新增页面**：登录/注册页、简历列表页、编辑器页（`/editor/:id`）。
- **自动保存**：编辑防抖（debounce）调用 `PUT /api/resumes/{id}`；登录后从 `GET /api/resumes/{id}` 拉取数据。
- 保留「导出 JSON / 导入 JSON」作为离线备份能力。

### 7.2 管理端 `admin-ui`

- 全新工程，Ant Design + axios，对接 `/admin/**`。

---

## 8. 实现细节与注意事项

以下为编码阶段必须遵守的约定与易错点，避免返工。

1. **命名映射**：DTO/TS 用 camelCase，DB 用 snake_case，统一由 MyBatis-Plus 转换；切勿手工在接口层混用下划线。

2. **id 类型**：前端 `id` 为字符串（现 `uid()` 生成短串）。后端 MySQL 主键 `BIGINT` 自增，**VO 序列化时转 String**，避免 JS 大数精度丢失。导入旧 JSON 时保留外部 id 作为明细业务 ID，与自增主键解耦（明细表可另加 `ref_id` 列，或用 `resume_section.custom_id` 关联方式处理）。

3. **custom section 关联**：现有前端 `section.id === customSection.id`（同一 id 两处引用）。持久化时 `resume_section.custom_id` 存该 id，`resume_custom_section` 需另存一份可回查的 id；保证删除自定义板块时同步删除对应 section 条目。

4. **富文本防 XSS**：`description`/`content`/`summary` 只允许纯文本 + `**加粗**` 语法。后端入库前可做白名单校验（拒绝 `<`、`>` 或转义）；前端 `textRenderer` 渲染时不得使用 `dangerouslySetInnerHTML` 拼接原始 HTML。

5. **日期字段**：保持字符串（`yyyy-MM-dd` / `yyyy-MM`），后端仅做格式校验，不做时区/时间戳转换，避免前后端显示不一致。

6. **头像仅存 URL**：校验 `http/https` 前缀；本地文件上传不在本次范围（后续接 OSS 再扩展 `avatar` 来源）。

7. **事务边界**：`PUT`（整体保存）需在单事务内「删旧明细 + 插新明细 + 更新主表」，任一步失败整体回滚；JSON 模式实现同构的原子写（写临时文件 + rename）。

8. **乐观锁**：`resume.version` 字段；`PUT` 携带客户端版本号，更新时 `WHERE version = ?`，影响行数为 0 → 返回 `409` 冲突，前端提示「内容已在别处更新，请刷新」。

9. **缓存一致性**（仅 `resume.cache.type=redis` 时生效，`none` 时跳过全部缓存逻辑）：
   - 顺序：先更新 DB → 成功后删除缓存（而非先删缓存），避免并发读回填旧值；
   - 防击穿：对热点 `resume:detail:{id}` 加锁或允许短暂不一致；
   - 防穿透：不存在的 id 缓存空标记（短 TTL）；
   - 防雪崩：TTL 加随机抖动（如 `ttl ± 10%`）。

10. **分页上限**：`size` 上限 100，防止大分页拖垮 DB；管理端统计使用聚合 SQL 或定时汇总表。

11. **权限**：`/admin/**` 通过独立 Security 规则 + `@PreAuthorize("hasRole('ADMIN')")`；JWT 需携带 `role`，且服务端以 DB 实时角色为准（避免仅信令牌中的角色导致权限滞留）。

12. **脱敏**：任何含 `sys_user` 的返回不得包含 `password`、`status`（对外）；用 VO 显式裁剪。

13. **CORS**：后端集中配置 `resume.cors.allowed-origins`，仅放行前端 origin，禁止 `*`。

14. **前端鉴权处理**：axios 拦截器对 `401` 统一跳登录页并清理本地 token；`403` 提示越权；`409` 提示版本冲突。

15. **自动保存并发**：前端防抖 + 请求串行（上次未返回不发下一次）；携带 `version` 校验；保存失败给出可重试提示，不静默丢数据。

16. **旧数据迁移**：现有 `localStorage` 与导出的 JSON 均保持 `ResumeData` 形状，可直接 `POST /api/resumes/import`；上线前提供一次性「本地上传 JSON → 导入」入口，避免用户数据丢失。

17. **列表精简**：列表接口返回字段最小集（不含明细/富文本），详情接口才返回完整聚合，减轻传输与 Redis 体积。

18. **错误信息不泄露内部细节**：500 对外统一「服务器繁忙」，日志记录堆栈；404 不区分「不存在/无权限」（统一 404 防枚举他人资源）。

19. **单会话一致性**：`token_version` 是唯一权威源，Redis 仅加速；校验时 Redis 未命中必须回源 DB，否则 `cache.type=none` 下踢线失效。登录/登出的 version 递增必须原子（`UPDATE ... + 1`）并随事务提交，防止「递增未落库」导致被踢的旧 Token 仍有效。前端收到被踢的 `401` 需区分「登录过期」与「被顶号」，后者给出明确提示并跳登录页。

---

## 9. 测试用例

### 9.1 单元测试（Service / Repository / 映射）

| 编号 | 场景 | 前置 | 操作 | 期望 |
|------|------|------|------|------|
| UT-01 | MySQL 聚合读写 | 空库 | 写入完整 ResumeDTO → 读取 | 读回结果与写入逐字段一致（含排序、显隐） |
| UT-02 | JSON 聚合读写 | `type=json` | 同 UT-01 | 同上，且文件/文档落盘成功 |
| UT-03 | 存储等价性 | 同一份数据 | 分别用 MySQL/JSON 写读 | 两份读回结果**完全一致** |
| UT-04 | 明细级联删除 | 已存简历 | 更新为更少明细 | 旧明细记录被删除，无孤儿数据 |
| UT-05 | 乐观锁冲突 | 同 version | 两次并发更新 | 第二次返回冲突（影响行数 0） |
| UT-06 | custom 关联删除 | 含自定义板块 | 删除自定义板块 | 对应 section 条目同步移除 |
| UT-07 | 日期/颜色校验 | 非法输入 | `accentColor="red"`、`endDate="2026/1"` | 校验失败返回 400 |
| UT-08 | 富文本 XSS 校验 | 含 `<script>` | 保存 description | 被拒绝或转义，不落库原始 HTML |

### 9.2 接口 / 集成测试

| 编号 | 场景 | 操作 | 期望 |
|------|------|------|------|
| IT-01 | 注册-登录-查我 | 注册→登录→`/me` | 返回正确用户，无 password |
| IT-02 | 重复注册 | 同名再注册 | 返回 409 |
| IT-03 | 未带 Token 访问详情 | 无 Authorization | 返回 401 |
| IT-04 | 伪造/过期 Token | 篡改 payload | 返回 401 |
| IT-05 | 禁用用户登录 | status=0 | 返回 403 |
| IT-06 | 增删改查简历 | 新建→改→查→删→查 | 各步骤符合契约，删除后 404 |
| IT-07 | 导入导出 | 导出 JSON→导入 | 数据一致，生成新简历 |

### 9.3 隔离 / 安全测试

| 编号 | 场景 | 操作 | 期望 |
|------|------|------|------|
| SC-01 | 跨用户读 | 用户 A 读用户 B 的 resumeId | 403/404，不泄露 |
| SC-02 | 跨用户改/删 | 用户 A PUT/DELETE B 的 resume | 403，数据不变 |
| SC-03 | 列表隔离 | 用户 A 拉列表 | 仅返回 A 自己的简历 |
| SC-04 | 普通用户访问 /admin | role=USER 调 `/admin/users` | 403 |
| SC-05 | 管理员跨用户 | role=ADMIN 调 `/admin/resumes` | 正常返回，含 username |

### 9.4 缓存测试

| 编号 | 场景 | 操作 | 期望 |
|------|------|------|------|
| CA-01 | 命中缓存 | 首次 GET 详情后再次 GET | 第二次不再查库（可用日志/计数验证） |
| CA-02 | 写后失效 | PUT 更新后 GET | 返回最新值，旧缓存已删除 |
| CA-03 | TTL 过期 | 等待 TTL | 过期后回源重建缓存 |
| CA-04 | 缓存不越权 | 用户 A 命中 B 的缓存 key | 因 key 含 resumeId 且读前校验归属，即便命中也被拦截 |

### 9.5 边界测试

| 编号 | 场景 | 操作 | 期望 |
|------|------|------|------|
| BD-01 | 名称过长 | username=51 字符、name=60 字符 | 400 校验失败 |
| BD-02 | 明细超限 | 某项数组 101 条 | 400 |
| BD-03 | 分页上限 | `size=200` | 被限制为 100 或 400 |
| BD-04 | 空简历 | 全字段空 | 正常保存，明细为 `[]` |
| BD-05 | 大文本 | description 含 1 万字 | 正常保存（TEXT 足够） |

### 9.6 单点登录（单会话）

| 编号 | 场景 | 操作 | 期望 |
|------|------|------|------|
| SS-01 | 新登录踢旧 | 设备 A 登录→设备 B 用同账号登录→设备 A 用旧 Token 请求 | A 的旧 Token 返回 401 |
| SS-02 | 最新会话有效 | 设备 B 登录后用新 Token 请求 | 正常 200 |
| SS-03 | 登出失效 | 登录→登出→用原 Token 请求 | 返回 401 |
| SS-04 | 被踢后重登 | 旧 Token 失效→重新登录 | 获得新 Token，正常访问 |
| SS-05 | 无 Redis 踢线 | `cache.type=none` 下复现 SS-01 | 同样被踢，DB 兜底生效 |
| SS-06 | 并发登录 | 两个请求几乎同时登录 | 最终仅一个有效版本，另一个旧 Token 失效 |

---

## 10. 实现顺序（按步骤规划）

> 依赖关系决定顺序：先「数据模型与存储」→「认证与隔离」→「缓存」→「接口契约」→「前端对接」→「管理端」→「测试与部署」。

### 阶段 0：工程骨架
- [ ] **S0-1** 新建 `resume-server`（Spring Boot 3 + Maven）与 `admin-ui` 工程。
- [ ] **S0-2** 编写 `application.yml`（数据源/Redis/JWT/存储模式/CORS）。
- [ ] **S0-3** 搭好统一响应体 `ApiResponse`、错误码枚举、全局异常处理器。
- 验收：空接口返回 `{ code, message, data }` 规范结构。

### 阶段 1：数据库与存储层
- [ ] **S1-1** 执行 `db/schema.sql` 建表（含 `version` 乐观锁列）。
- [ ] **S1-2** 定义 `ResumeRepository` 抽象接口 + `ResumeDTO` 聚合模型。
- [ ] **S1-3** 实现 `MysqlResumeRepository`（聚合 ↔ 11 表事务读写）。
- [ ] **S1-4** 实现 `JsonResumeRepository`（文件/JSON 列）。
- [ ] **S1-5** 用同一份测试数据验证两种实现读回结果等价（UT-03）。
- 验收：`type=json` 与 `=mysql` 均可完整读写同一简历。

### 阶段 2：认证与隔离
- [ ] **S2-1** 用户表 CRUD + BCrypt 密码。
- [ ] **S2-2** JWT 签发/校验、过滤器、`UserContext`。
- [ ] **S2-3** 简历接口接入 `user_id` 隔离 + 资源归属校验。
- [ ] **S2-4** 编写越权用例（SC-01~SC-05）验证隔离。
- [ ] **S2-5** 实现单会话：`token_version` 签发递增 + 请求校验 + 登出递增，Redis 加速、DB 兜底（对齐 4.8）。
- 验收：跨用户访问返回 403；新登录后旧 Token 立即 401（单会话踢线，SS-01~SS-06 通过）。

### 阶段 3：缓存（可选，`resume.cache.type=none` 可整体跳过本阶段）
- [ ] **S3-1** 定义缓存抽象接口 + `@ConditionalOnProperty` 双实现（`RedisCacheService` / 无缓存直连），默认可跑通 `none` 模式。
- [ ] **S3-2** `type=redis` 时实现简历详情/列表 Cache-Aside。
- [ ] **S3-3** 写操作统一删除缓存（切面或显式调用），并验证 `type=none` 下无 Redis 依赖、功能完整。
- 验收：`type=redis` 时 CA-01~CA-04 通过；`type=none` 时全功能正常、无 Redis 连接诉求。

### 阶段 4：接口契约与文档
- [ ] **S4-1** 按第 5 节契约实现全部 DTO + 校验注解（`@Valid`）。
- [ ] **S4-2** 实现认证类、简历类、导入导出接口（对齐 5.3）。
- [ ] **S4-3** 接入 swagger（springdoc），输出接口文档作为前端联调基准。
- 验收：接口请求/响应与第 5 节契约逐字一致。

### 阶段 5：用户端前端对接
- [ ] **S5-1** 引入 axios（JWT 拦截器）、react-router。
- [ ] **S5-2** 登录/注册页、简历列表页。
- [ ] **S5-3** 编辑器页 `Zustand` 持久化改为 API 读写 + 防抖自动保存（带 version）。
- [ ] **S5-4** 保留 PNG/PDF 导出、JSON 导入导出。
- 验收：登录后编辑，刷新/换设备数据不丢；冲突 409 有提示。

### 阶段 6：管理端
- [ ] **S6-1** 用户管理（列表/搜索/禁用）。
- [ ] **S6-2** 简历管理（全量列表/预览/删除/导出）。
- [ ] **S6-3** 统计看板（用户数/简历数/模板分布）。
- 验收：管理员可跨用户管理，普通用户无入口。

### 阶段 7：测试与部署
- [ ] **S7-1** 补齐单元/集成/边界测试（第 9 节全量用例）。
- [ ] **S7-2** 环境分离（dev/prod profile）。
- [ ] **S7-3** 部署：前端 Nginx 托管 + 后端 jar + MySQL + Redis。
- 验收：见第 11 节验收标准全部通过。

---

## 11. 验收标准

- [ ] 用户注册/登录后，简历数据保存到服务端，换设备可恢复。
- [ ] `resume.storage.type=json` 与 `=mysql` 均能完整读写同一份简历且数据等价。
- [ ] 用户 A 无法访问/修改用户 B 的简历（越权 403）。
- [ ] 同一用户新登录后旧登录令牌立即失效（单会话踢下线），且 `cache.type=none` 下同样生效。
- [ ] 简历详情命中 Redis 缓存，写操作后缓存失效、数据一致。
- [ ] `resume.cache.type=none` 时应用无需 Redis 即可完整运行，功能与 `=redis` 等价。
- [ ] 管理端可查看全量用户/简历、可禁用用户、删除简历、查看统计。
- [ ] 导出 PNG/PDF 功能在前后端分离后仍正常。
- [ ] 全部接口输入/输出符合第 5 节契约，测试用例（第 9 节）全量通过。

---

## 12. 风险与注意事项

1. **数据一致性**：MySQL 模式多次写子表需包事务；缓存与 DB 需写后失效。
2. **JSON 与 MySQL 模式等价性**：两种存储后端需通过同一套聚合模型保证可互相切换、结果一致（建议用同一份测试数据做等价校验）。
3. **迁移兼容**：现有 `localStorage`/JSON 导出的 `ResumeData` 结构应保持，使旧备份可 `POST /api/resumes/import` 导入。
4. **隔离红线**：所有用户端接口必须显式执行 `user_id` 过滤；管理端仅 `ADMIN` 可达。
5. **令牌安全**：JWT 密钥入配置/环境变量，禁止硬编码；令牌过期与刷新策略需明确。
6. **附件/头像**：当前头像为 URL 填写，若需上传需引入对象存储（OSS），本次范围外，暂保持 URL 方案。
7. **id 精度**：BIGINT 主键必须转 String 返回，否则 JS 端精度丢失导致无法定位明细。

---

## 附录：改造前后对照

| 维度 | 改造前 | 改造后 |
|------|--------|--------|
| 数据存储 | localStorage / 本地 JSON | MySQL 或 JSON 文件（可切换） |
| 用户体系 | 无 | 注册/登录 + JWT |
| 数据隔离 | 单机天然隔离 | 服务端 user_id 强制隔离 |
| 缓存 | 无 | Redis（详情/列表） |
| 管理能力 | 无 | 独立管理后台 |
| 接口契约 | 无 | 统一响应/错误码/DTO 校验 |
| 并发控制 | 无 | 乐观锁 version |
| 测试 | 无 | 单元/集成/隔离/缓存/边界用例 |
| 部署 | Electron / 静态服务 | 前端 Nginx + 后端 jar + MySQL + Redis |