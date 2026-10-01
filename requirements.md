# 需求文档：简历生成器产品增强路线图

> 版本：v1.1
> 定位：本文档描述简历生成器的**下一阶段产品增强需求**，含功能拆解、数据模型、接口、前后端改动、验收标准与实现顺序（从易到难）。
> 关联文档：`plan.md`（改造规划）、`spec.md`（上一轮增强需求）、`README.md`（架构与技术栈）。

---

## 1. 背景与目标

当前系统已完成前后端分离改造，具备「多模板简历编辑 + 导出 + 多用户隔离 + 管理后台」的核心能力。为提升产品竞争力与商业价值，本轮规划增强需求：

1. 管理后台报表精细化（图表 + PV/UV，分钟级）
2. 简历社区（分享 / 学习 / 点赞 / 收藏 / 评价）
3. 简历定点分享（key / 短链接）
4. 模板数据化（template 表 + 三套内置，后续可扩展绘制 / 市场）
5. AI 修改 / 完善简历（DeepSeek）
6. 面试题目生成（DeepSeek）
7. 亮点 / 技术挑战能力（前瞻候选）
8. 用户数据后台（观阅 / 评论数据与报表）
9. 检索与性能优化（ES 全文检索 / OSS / MQ / 缓存限流 / 慢查询优化）

**目标**：在复用现有架构（Controller → Service → Repository、双存储、可选缓存、JWT 单会话、`{code,message,data}` 统一响应）的前提下，按「依赖关系 + 变现价值 + 技术风险」从易到难分阶段落地，每阶段可独立上线。

---

## 2. 需求总览与实现顺序

> 排序原则：先做低风险、自闭环、可复用基建；后做依赖外部服务、技术风险高的功能。

| 阶段 | 编号 | 需求 | 难度 | 依赖 | 价值 |
|------|------|------|------|------|------|
| 一 | R1 | 简历定点分享（key/短链，域名 yml 配置） | ⭐ 低 | 无 | 传播获客 |
| 一 | R2 | 管理后台报表（图表 + PV/UV，分钟级） | ⭐⭐ 低-中 | 无 | 运营决策 |
| 一 | R9 | 用户数据后台（观阅/评论数据 + 报表） | ⭐⭐ 低-中 | R2 埋点 | 留存/成就感 |
| 一 | R4 | 模板数据化（template 表 + 三套内置） | ⭐ 低 | 无 | 架构演进 |
| 二 | R3 | 简历社区（规则引擎先审后发 + 高热度再审） | ⭐⭐⭐ 中 | R1 渲染、R2 埋点 | 内容生态 |
| 三 | R5 | 面试题目生成（DeepSeek） | ⭐⭐⭐⭐ 中高 | AI 网关 | 增值 |
| 三 | R6 | AI 修改/完善简历（DeepSeek） | ⭐⭐⭐⭐⭐ 高 | AI 网关 | 增值 |
| 四 | R8 | 模板可视化绘制 + 市场、亮点候选 | 高/视项 | 渲染引擎抽象 | 生态/壁垒 |
| 贯穿 | R10 | 检索与性能优化（ES/OSS/MQ/缓存限流/慢查询） | ⭐~⭐⭐⭐ | 视子项 | 性能/体验 |

**横切基建**
- **AI 能力层**（Spring AI：`ChatClient` + `EmbeddingModel` + `VectorStore`，OpenAI 兼容协议默认接 DeepSeek；叠加 RAG 检索增强 + Skill 函数化技能编排），R5/R6/R8 复用。
- **埋点/聚合链路**（分钟级，带 `resume_id` 维度），R2/R3/R9 复用。
- **免登录公开读取 + 隐私脱敏**（R1 落地，R3 复用）。

---

## 3. 现状与约束（需遵守的既有约定）

- **统一响应体**：所有接口 `{ code, message, data }`；错误码 `0/400/401/403/404/409/500`。
- **数据隔离红线**：用户端查询强制 `user_id` 过滤；管理端仅 `ADMIN`（`/admin/**`）。
- **命名/类型**：camelCase、`id` 转 String、日期字符串（`yyyy.MM`/`yyyy-MM`，可带日，拒绝斜杠）。
- **防 XSS**：富文本拒绝 `<script>`/`<iframe>`/`javascript:` 等；`accentColor` 仅 `#RGB`/`#RRGGBB`。
- **可选依赖风格**：缓存 `redis|none`、存储 `mysql|json`；新增能力沿袭「可开关」。
- **关键文件**：
  - 后端：`controller/`、`service/`、`repository/`、`mapper/`、`entity/`、`dto/`、`security/`、`common/`、`config/`（新增 `AiProperties`、`ShareProperties`）。
  - 用户端：`src/api/`、`src/store/`、`src/pages/`、`src/components/templates/`（三套硬编码模板组件）。
  - 管理端：`admin-ui/src/`。
  - 表：`sys_user`、`resume` + 9 张明细表（`db/schema.sql`）。

---

## 4. 需求细则

### R1 简历定点分享（key / 短链接）— 阶段一 ⭐

**定位**：用户为某份简历生成免登录只读分享入口，可复制 key、短链或二维码。为社区、分享页 SEO 打地基。

**功能点**
1. 生成分享：选择简历 → 生成唯一 `shareKey`（6–8 位随机串）与短链 `{baseUrl}/s/{key}`。
2. 只读查看：免登录访问短链/输 key，渲染只读简历（复用模板渲染，隐藏编辑）。
3. 隐私脱敏：默认隐藏手机/邮箱，可选项「展示联系方式」。
4. 生命周期：有效期（永久/7天/30天/自定义）、可选访问密码、随时撤销、展示访问次数。
5. 防滥用：随机 key 防枚举、查询限流、撤销/过期即刻失效。

**短链域名配置**（新增 `ShareProperties`，写入 yml）：

```yaml
resume:
  share:
    base-url: https://resume.example.com   # 短链域名，前端根据此拼接分享链接
    default-ttl-days: 30                   # 默认有效期（0=永久）
```

**数据模型**

```sql
CREATE TABLE resume_share (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  resume_id   BIGINT      NOT NULL,
  share_key   VARCHAR(16) NOT NULL,
  password    VARCHAR(64) DEFAULT NULL COMMENT '访问密码(BCrypt,可空)',
  expire_at   DATETIME    DEFAULT NULL COMMENT 'NULL=永久',
  show_contact TINYINT    NOT NULL DEFAULT 0,
  view_count  BIGINT      NOT NULL DEFAULT 0,
  status      TINYINT     NOT NULL DEFAULT 1 COMMENT '1有效 0撤销',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_share_key (share_key),
  KEY idx_user (user_id),
  KEY idx_resume (resume_id)
) ENGINE=InnoDB;
```

**接口**
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/api/shares` | 生成分享 | 登录 |
| GET | `/api/shares` | 我的分享列表 | 登录 |
| DELETE | `/api/shares/{key}` | 撤销 | 登录 |
| GET | `/api/public/shares/{key}` | 免登录读取只读简历 | 公开 + 可选密码 |

**前端**：编辑器 `Toolbar`/预览区「分享」按钮 → 弹窗展示 key/短链/二维码/有效期/密码/撤销；新增 `/s/:key` 只读页。

**验收标准**
- 未登录访客可经短链或输 key 查看只读简历，联系方式默认隐藏；密码保护生效；撤销/过期即刻失效。
- 短链域名取自 yml `resume.share.base-url`，改配置即全局生效。

---

### R2 管理后台报表（图表 + PV/UV，分钟级）— 阶段一 ⭐⭐

**定位**：将 `DashboardPage` 升级为图形化报表，新增分钟级 PV/UV 流量统计。**PV/UV 按分钟粒度**采集与聚合，为运营和用户后台（R9）共用数据源。

**功能点**
1. **埋点采集**：用户端/分享页/社区页上报页面访问事件（PV、UV、来源、`resumeId`）。
   - UV 口径：登录用户按 `user_id` 去重；匿名按 `deviceId`（localStorage 稳定随机串）去重。
   - 分钟级：原始事件直接落 `access_log`（含时间戳），分钟级聚合到 `stat_minute`。
2. **指标聚合**：`stat_minute` 按「分钟 + 指标 + 维度」预聚合；看板按分钟/小时/天向上 rollup。
3. **图表看板**（admin-ui 引入图表库，建议 ECharts 或 `@ant-design/charts`）：
   - 概览卡：总用户/总简历/今日 PV/今日 UV/实时在线。
   - 趋势：近 24h 分钟级 PV/UV、近 30 天日粒度新增用户/简历。
   - 分布：模板使用分布、访问来源、简历模块完整度。

**数据模型**

```sql
CREATE TABLE access_log (           -- 原始埋点（异步批量写入）
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  user_id    BIGINT   DEFAULT NULL,
  device_id  VARCHAR(64) DEFAULT NULL,
  event_type VARCHAR(20) NOT NULL COMMENT 'page_view/share_view/comment/...',
  page       VARCHAR(100) DEFAULT NULL,
  resume_id  BIGINT   DEFAULT NULL COMMENT '关联简历, 供 R2/R9 按简历统计',
  ip_hash    VARCHAR(64) DEFAULT NULL,
  ua         VARCHAR(255) DEFAULT NULL,
  ts         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ts (ts),
  KEY idx_resume_ts (resume_id, ts),
  KEY idx_device_ts (device_id, ts)
) ENGINE=InnoDB;

CREATE TABLE stat_minute (          -- 分钟级预聚合
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  stat_time  DATETIME NOT NULL COMMENT '分钟粒度',
  metric     VARCHAR(32) NOT NULL COMMENT 'pv/uv/new_user/new_resume/...',
  dimension  VARCHAR(64) NOT NULL DEFAULT '' COMMENT '来源/模板/resumeId等',
  value      BIGINT   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_time_metric_dim (stat_time, metric, dimension),
  KEY idx_metric_time (metric, stat_time)
) ENGINE=InnoDB;
```

**接口**（管理端 `/admin/stats/*`，仅 ADMIN）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/stats/overview` | 概览卡指标 |
| GET | `/admin/stats/trend?granularity=minute|hour|day&from=&to=` | 趋势序列 |
| GET | `/admin/stats/pvuv?from=&to=` | 区间 PV/UV |

**前端**：`DashboardPage.tsx` 重写接入图表；用户端 `src/utils/` 新增埋点上报，路由切换与页面挂载时上报。

**验收标准**
- 看板支持分钟/小时/天粒度切换，分钟级数据可查（近 24h）。
- PV/UV 与埋点口径一致，UV 去重正确。

**难点**：分钟级写入量较大，采用异步队列/批量落库；聚合幂等（同分钟可重跑）。

---

### R9 用户数据后台（观阅/评论数据 + 报表）— 阶段一 ⭐⭐

**定位**：新增「个人数据后台」，用户查看自己简历**被观看、被评价**的数据与趋势报表，复用 R2 埋点与社区数据。

**功能点**
1. **概览**：我的简历总数、总观阅量（PV/UV）、总点赞/收藏/评论、分享访问量。
2. **单份简历详情**：该简历的观看 PV/UV 趋势（分钟/小时/天）、评论列表（来自社区）、点赞/收藏数。
3. **报表图**：折线趋势 + 总量卡片，可按简历筛选。
4. **数据口径**：观阅来自 `access_log`（`resume_id` 关联）；点赞/收藏/评论来自社区表；分享访问来自 `resume_share.view_count`。

**数据模型**：复用 `access_log`、`stat_minute`、`community_*`、`resume_share`，无新增表。

**接口**（`/api/stats/my/*`，需登录，强制 `user_id` 过滤）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/stats/my/overview` | 我的数据概览 |
| GET | `/api/stats/my/resumes` | 按简历维度列表（观阅/点赞/评论/分享数） |
| GET | `/api/stats/my/resumes/{id}/trend?granularity=` | 单简历观阅趋势 |
| GET | `/api/stats/my/resumes/{id}/comments` | 该简历收到的评论列表 |

**前端**：个人主页（`ResumeListPage`）新增「数据中心」入口，新增 `MyStatsPage`；图表复用图库。

**验收标准**
- 登录用户仅能查自己的观阅/评价数据，跨用户返回 404/403（隔离）。
- 观阅趋势与 R2 埋点口径一致；评论/点赞/收藏数据与社区一致。

---

### R4 模板数据化（template 表 + 三套内置）— 阶段一 ⭐

**定位**：将模板从「硬编码三套」演进为「模板表驱动」。**当前阶段**仅建 `template` 表并内置 `classic`/`modern`/`minimal` 三套官方模板，`resume.template_id` 指向模板编码；可视化绘制与模板市场后置到阶段四（R8）。

**功能点**
1. **模板表**：`template` 表存模板元信息；内置模板 `type=official`、`schema=NULL`（渲染仍由前端三套硬编码组件完成，以 `code` 映射）。
2. **模板列表接口**：前端编辑器模板选择改为从 `GET /api/templates` 拉取，而非硬编码枚举。
3. **预留扩展**：`schema` 字段为未来自定义模板（拖拽绘制）预留；`type=user` 表示用户/社区模板。

**数据模型**

```sql
CREATE TABLE template (
  id          BIGINT   NOT NULL AUTO_INCREMENT,
  code        VARCHAR(50) NOT NULL COMMENT '模板唯一编码: classic/modern/minimal/...',
  name        VARCHAR(100) NOT NULL,
  type        VARCHAR(20) NOT NULL DEFAULT 'official' COMMENT 'official/user',
  category    VARCHAR(50) DEFAULT NULL COMMENT '风格分类',
  schema      JSON     DEFAULT NULL COMMENT '自定义模板渲染Schema(内置模板为NULL)',
  thumbnail   VARCHAR(500) DEFAULT NULL,
  sort_order  INT      NOT NULL DEFAULT 0,
  status      TINYINT  NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB;
-- 初始化内置三套：classic / modern / minimal（type=official）
```

**接口**
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/api/templates` | 模板列表（上架状态） | 登录可选 |
| GET | `/api/templates/{code}` | 模板详情 | 登录可选 |
| 管理端 | `/admin/templates` CRUD | 新增/上下架模板 | ADMIN |

**前端**：`resume.ts` 的 `templateId` 枚举语义放宽为字符串编码；编辑器模板选择请求 `/api/templates`；三套模板组件以 `code` 映射不变。

**验收标准**
- 内置三套模板由表驱动可被列表接口返回，前端可正常切换。
- `resume.template_id` 存模板编码，新增模板后无需改前端即可出现在选择列表（渲染组件后续再实现）。

**后续（阶段四 R8）**：可视化绘制、`type=user`、模板市场、渲染引擎抽象。

---

### R3 简历社区（规则引擎先审后发 + 高热度再审）— 阶段二 ⭐⭐⭐

**定位**：把分享能力升级为内容社区。**审核策略：先审后发（规则引擎自动过滤），通过即上线；对高热度异常简历触发二次人工再审**。社区浏览匿名可看，点赞/收藏/评论需登录。

**功能点**
1. **发布**：从「我的简历」生成内容（自动脱敏联系方式），填标题/摘要/标签，生成封面。
2. **先审后发（规则引擎）**：发布即进入 `pending`，规则引擎自动判定——命中敏感词/违禁内容/格式异常 → `rejected`；否则 → `online`。
3. **高热度二次再审**：线上帖子浏览/点赞/举报达到阈值且触发异常信号（短时暴涨、举报数超限）→ 转 `review` 待人工复审。
4. **信息流**：公开、匿名可浏览，最新/最热排序、标签筛选、分页。
5. **互动（需登录）**：点赞、收藏、评论（楼中楼）；匿名访客点互动时引导登录。
6. **我的互动**：我发布的/点赞的/收藏的。
7. **治理**：管理端人工复审、下架、删除、举报处理。

**数据模型**

```sql
CREATE TABLE community_post (
  id            BIGINT   NOT NULL AUTO_INCREMENT,
  user_id       BIGINT   NOT NULL,
  resume_id     BIGINT   NOT NULL,
  title         VARCHAR(100) NOT NULL,
  summary       VARCHAR(500) DEFAULT NULL,
  tags          VARCHAR(255) DEFAULT NULL,
  cover_url     VARCHAR(500) DEFAULT NULL,
  audit_status  TINYINT  NOT NULL DEFAULT 0 COMMENT '0待审(pending) 1已上线(online) 2拒绝(rejected) 3人工复审(review) 4下架(offline)',
  audit_reason  VARCHAR(255) DEFAULT NULL COMMENT '拒绝/复审原因',
  like_count    BIGINT   NOT NULL DEFAULT 0,
  collect_count BIGINT   NOT NULL DEFAULT 0,
  comment_count BIGINT   NOT NULL DEFAULT 0,
  view_count    BIGINT   NOT NULL DEFAULT 0,
  report_count  INT      NOT NULL DEFAULT 0,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_status_time (audit_status, created_at)
) ENGINE=InnoDB;

CREATE TABLE community_like (
  id BIGINT NOT NULL AUTO_INCREMENT, post_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB;

CREATE TABLE community_collect (
  id BIGINT NOT NULL AUTO_INCREMENT, post_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_post_user (post_id, user_id), KEY idx_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE community_comment (
  id BIGINT NOT NULL AUTO_INCREMENT, post_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
  parent_id BIGINT DEFAULT NULL, content VARCHAR(1000) NOT NULL, status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), KEY idx_post (post_id), KEY idx_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE community_report (
  id BIGINT NOT NULL AUTO_INCREMENT, post_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
  reason VARCHAR(255) DEFAULT NULL, status TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), KEY idx_post (post_id)
) ENGINE=InnoDB;
```

**接口**（浏览匿名；互动需登录）
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/api/community/posts` | 信息流（仅 online） | 匿名可 |
| GET | `/api/community/posts/{id}` | 详情（online，含脱敏简历；登录则带回当前用户互动态） | 匿名可 |
| POST | `/api/community/posts` | 发布（触发规则引擎审核） | 登录 |
| POST | `/api/community/posts/{id}/like` | 点赞/取消 | 登录 |
| POST | `/api/community/posts/{id}/collect` | 收藏/取消 | 登录 |
| GET | `/api/community/posts/{id}/comments` | 评论列表 | 匿名可 |
| POST | `/api/community/posts/{id}/comments` | 评论 | 登录 |
| DELETE | `/api/community/comments/{id}` | 删自己评论 | 登录 |
| POST | `/api/community/posts/{id}/report` | 举报 | 登录 |
| 管理端 | `/admin/community/posts/{id}/audit` 等 | 人工复审/上下架/删除 | ADMIN |

**规则引擎**：独立 `AuditRuleEngine` 组件，可插拔规则（敏感词库、违禁内容、格式校验、去重）；规则可通过配置/后台词库热更新。

**前端**：新增 `CommunityPage`（列表）、`CommunityDetailPage`（详情+评论）、发布弹窗；`App.tsx` 增路由；`api/community.ts`。复用 R1 只读简历渲染。匿名浏览遇互动操作时弹登录引导。

**验收标准**
- 发布时不立即上线，经规则引擎过滤：通过→上线，命中违规→拒绝。
- 高热度异常帖子转人工复审（`review`），管理端可见并处理。
- 匿名可浏览信息流与详情（脱敏）；点赞/收藏/评论需登录。

**难点**：规则引擎准确性（可配词库/阈值）；点赞收藏计数一致性（Redis 计数 + 回写，`none` 降级 DB 事务）；高热度异常识别阈值需可调。

---

### R5 面试题目生成（Spring AI + RAG + Skill，默认 DeepSeek）— 阶段三 ⭐⭐⭐⭐

**定位**：基于某份简历一键生成「面试官可能问的问题 + 参考答案 + 考察点」，用于面试准备。技术栈选用 **Spring AI**，通过 **RAG** 引入题库 / JD 语料提升题目相关性，通过 **Skill** 拆分出题 / 答案 / 考察点等原子能力。

**前置**：AI 能力层基建（见下方「AI 能力层（Spring AI）」）。

**功能点**
1. 选简历 → 定向生成：指定目标岗位 / JD 后，先 RAG 检索再生成结构化题库。
2. 题库结构：`[{ category, question, answer, tips }]`，按 category（项目深挖 / 技术原理 / 行为面 / 场景题）分类。
3. Skill 编排：出题 / 答案+考察点 / JD 解析分别独立 skill，可单独重跑、按需组合。
4. 结果管理：保存为「面试题集」，查看 / 重新生成 / 导出（JSON / Markdown / 文本）。

**数据模型**

```sql
-- 知识库（RAG，R5/R6 共用；向量由 VectorStore 托管，MySQL 存元数据与原文）
CREATE TABLE ai_kb_entry (
  id BIGINT NOT NULL AUTO_INCREMENT, kb_type VARCHAR(32) NOT NULL COMMENT 'interview_q/jd/resume_sample/skill_prompt',
  code VARCHAR(64) DEFAULT NULL, title VARCHAR(200) DEFAULT NULL, content TEXT NOT NULL,
  metadata JSON DEFAULT NULL, status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_type_code (kb_type, code), KEY idx_type_status (kb_type, status)
) ENGINE=InnoDB;

CREATE TABLE interview_set (
  id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, resume_id BIGINT NOT NULL,
  title VARCHAR(100) DEFAULT NULL, target_role VARCHAR(100) DEFAULT NULL,
  questions TEXT COMMENT '结构化题库 JSON',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), KEY idx_user (user_id)
) ENGINE=InnoDB;
```

**接口**（`/api/ai/*`，需登录）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/interview/generate` | 生成题库（可选 `targetRole`/`jd`；流式 SSE） |
| GET | `/api/ai/interview/sets` | 我的题集列表 |
| GET | `/api/ai/interview/{id}` | 题集详情 |
| DELETE | `/api/ai/interview/{id}` | 删除题集 |
| 管理端 | `/admin/ai/kb/**` | 知识库 / 技能 prompt 维护 |

**AI 能力层（Spring AI，R5/R6/R8 共用）**
- 由 `spring-ai-bom` 统一版本；对话走 OpenAI 兼容协议直连 DeepSeek（`base-url=https://api.deepseek.com`，`model=deepseek-chat`）。
- `ChatClient`（一次性 / 流式 / 结构化输出）、`EmbeddingModel`（RAG 向量化，DeepSeek 无 embedding 端点需另配）、`VectorStore`（`memory|redis|pgvector`，默认内存）。
- Skill 以 `@Tool` 函数化，模型按意图自动路由；prompt 模板存 `ai_kb_entry(kb_type='skill_prompt')` 可热更新。
- 可开关：`ai.enabled=false`（无密钥降级）、`rag.enabled=false`（跳过检索直连）。

```yaml
ai:
  provider: deepseek                 # deepseek | openai | qwen（可插拔）
  openai:
    base-url: https://api.deepseek.com
    api-key: ${AI_API_KEY}           # 环境变量注入，不写明文
    chat-model: deepseek-chat
  embedding:
    provider: openai                 # openai | ollama | none
    model: text-embedding-3-small
    api-key: ${EMBEDDING_API_KEY}
  enabled: false                     # 无密钥时优雅降级，返回明确错误
rag:
  enabled: false
  store:
    type: memory                     # memory | redis | pgvector
  top-k: 5
  min-score: 0.65
```

- 治理：请求限流、token 用量记录、超时熔断、输入输出内容安全。

**前端**：新增「面试准备」入口（编辑器按钮 + 独立页），分类卡片展示、导出、流式打字机效果；生成时可填目标岗位 / 粘贴 JD。

**验收标准**
- 选择简历后可生成结构化题库，内容与简历相关（RAG 命中上下文生效）。
- 指定岗位 / JD 后更定向；结果可保存 / 查看 / 导出 / 删除；限流 / 超时 / 密钥缺失返回明确错误。

**难点**：DeepSeek 输出 JSON 稳定性（JSON Schema + 重试 + 容错）；embedding 需另配模型；RAG 检索质量与成本；Skill 路由准确性；个人信息脱敏后调用。

---

### R6 AI 修改 / 完善简历（Spring AI + RAG + Skill，默认 DeepSeek）— 阶段三 ⭐⭐⭐⭐⭐

**定位**：AI 润色、扩写、补全经历描述与自评，或给整体优化建议，用户逐条采纳合并回简历。技术栈 **Spring AI**：RAG 引入优秀样例 / 写作范式，Skill 拆分润色 / 扩写 / 建议 / 差异合并。

**前置**：复用 R5「AI 能力层」。

**功能点**
1. **润色 rewrite**：原文 → 更正式表述，采纳替换。
2. **扩写 expand**：要点 → STAR 结构化描述（RAG 命中动词库 / 量化范式作 few-shot）。
3. **建议 suggest**：全文分析 → 改进建议列表（不改稿）。
4. **采纳合并 apply**：某条「应用」→ 字段级 diff → 乐观锁回写（复用 `PUT`/`PATCH` 语义）。

**RAG 知识库**（`ai_kb_entry`）：`resume_sample`（优秀经历 / 自评样例）、`writing_style`（动词库 / 量化句式 / STAR 模板）。

**Skill**（`@Tool`，按意图路由）：`resume.rewrite` / `resume.expand_star` / `resume.suggest` / `resume.apply_diff`。

**数据模型**

```sql
CREATE TABLE ai_generation_log (
  id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, resume_id BIGINT DEFAULT NULL,
  task_type VARCHAR(32) NOT NULL COMMENT 'rewrite/expand/suggest/interview',
  input_hash VARCHAR(64) DEFAULT NULL, output TEXT,
  token_usage INT NOT NULL DEFAULT 0, status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), KEY idx_user (user_id)
) ENGINE=InnoDB;
```

**接口**（`/api/ai/resume/*`，需登录）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/resume/rewrite` | 润色 |
| POST | `/api/ai/resume/expand` | 扩写（STAR） |
| POST | `/api/ai/resume/suggest` | 全文建议 |
| POST | `/api/ai/resume/apply` | 采纳合并（乐观锁保存） |

**前端**：各模块编辑器（`ExperienceEditor` 等）加 AI 按钮；建议列表逐条采纳/忽略；采纳后乐观锁保存，冲突提示（409）。

**验收标准**
- 润色/扩写返回可用文本，采纳正确回写且不破坏其他数据。
- 建议模式不改稿；采纳走乐观锁，冲突有提示；生成记录可追溯；RAG 提升质量，`rag.enabled=false` 可降级。

**难点**：结构化输出对齐 `ResumeDTO`（JSON Schema 约束）；防 AI 虚构经历（prompt 只润色不编造 + 输出校验）；diff/apply 防误删字段；配额计费。

---

### R8 亮点 / 技术挑战能力（前瞻候选）— 阶段四

> 候选池，按「差异化 + 可行性」排序，含模板绘制/市场这一已明确后置项。

| 候选 | 一句话 | 价值 | 技术挑战 |
|------|--------|------|----------|
| A. 模板可视化绘制 + 市场 | R4 建表后，允许 `type=user` 自定义模板，拖拽布局 + 字段绑定，发布/审核/一键用 | 高（生态） | 渲染引擎抽象 + Schema 安全 |
| B. AI 简历评分 + JD 匹配度 | 输入 JD 量化匹配度（ATS 打分）+ 建议 | 高（付费） | 评分模型 + 可解释性 |
| C. 简历版本历史/时光机 | 保存快照，diff 回放/回滚 | 高（体验） | 版本存储与 diff |
| D. 分享页 SEO/社交卡片 | 公开分享/社区详情 SSR 快照（OpenGraph） | 中（传播） | SSR/无头渲染 |
| E. 简历水印防爬 | 导出/查看加水印 + 盲水印溯源 | 中（安全） | 盲水印算法 |

**推荐顺序**：先 A（承接 R4 模板表、复用 AI 网关）→ B → C → 其余按需。

> 候选 A~E 均已展开为详细 spec：`R8-A1/A2/A3`（模板编辑器 / 渲染引擎 / 市场）、`R8-B` AI 简历评分与 JD 匹配度、`R8-C` 简历版本历史 / 时光机、`R8-D` 分享页 SEO / 社交卡片、`R8-E` 简历水印防爬。

---

### R10 检索与性能优化 — 贯穿各阶段 ⭐～⭐⭐⭐

**定位**：在功能需求之外，补齐数据检索与系统性能。核心是将简历 / 社区 / 模板搜索从 MySQL `LIKE` 升级为 **Elasticsearch 全文检索**，并配套对象存储、异步化、缓存限流、慢查询优化。

> 落地顺序：先做低风险快赢（缓存/限流、慢查询索引），再做对象存储与异步化基建，最后落地 ES（依赖异步同步）。

#### O1 Elasticsearch 全文检索（核心）⭐⭐⭐
- 现状：`/api/resumes?keyword=`、`/admin/resumes`、社区/模板搜索均走 MySQL `LIKE`，无法对技能名、项目描述、公司等文本做分词匹配。
- 方案：ES 仅作**检索加速层**，MySQL 仍是权威存储。
  - 索引文档：简历（`title`、`personal.summary`、`skills[].name`、`experience[].company/position/description`、`projects[].name/description`、`education[].school/major`）、社区帖（`title/summary/tags`）、模板（`name/category`）。
  - 同步：写入/更新时**异步同步**（复用 O3 MQ）到 ES，最终一致 + 失败重试。
  - 回源：ES 命中返回 `id`，详情再回 MySQL 聚合读取；ES 不可用时降级 MySQL `LIKE`。
  - 可开关：`search.engine=mysql|elasticsearch`（沿袭可选依赖风格）。
- 接口升级：`GET /api/resumes?keyword=`、`GET /api/community/posts?keyword=`、`GET /api/templates?keyword=`、`/admin/resumes?keyword=` 支持分词 / 高亮 / 相关性排序。
- 数据隔离：检索时强制按 `user_id` 过滤（用户端），管理端独立索引策略，防止越权命中。

#### O2 对象存储 OSS ⭐⭐
- 现状：`avatar`/`cover_url`/`thumbnail` 仅 URL 填写，社区封面、模板缩略图、导出文件需要上传能力。
- 方案：接入对象存储（阿里云 OSS / 自建 MinIO），统一 `ObjectStorageService` 抽象；支持后端中转或签名 URL 直传；提供缩略图 / 水印图片处理。

#### O3 消息队列 + 异步化 ⭐⭐
- 现状：埋点直写库、AI 同步阻塞、ES 同步需解耦、导出 / 聚合为耗时任务。
- 方案：引入消息队列抽象（`MessageQueue`，**先以 Redis Stream 起步**，后续兼容 RabbitMQ / Kafka）；异步写 `access_log`、异步 AI 生成、异步聚合 `stat_minute`、异步同步 ES、异步导出大文件；失败重试 + 死信队列。

#### O4 多级缓存与限流 ⭐
- 现状：仅 Redis 简历详情/列表缓存，公开接口与 AI 接口无保护。
- 方案：Caffeine 本地缓存（热点模板、模板列表、无状态配置）+ Redis 二级缓存；公开接口（分享查看、社区信息流）与 AI 接口令牌桶限流；防穿透/击穿/雪崩对齐既有缓存约定。

#### O5 慢查询与索引优化 ⭐
- 现状：统计聚合、社区排序、深分页可能出现慢查询。
- 方案：接入慢查询监控；`access_log`/`stat_minute`/`community_post` 热字段建组合索引；聚合走预聚合表；深分页改用游标（`id > last`）。

#### O6 CDN 与前端构建优化 ⭐
- 方案：前端产物 CDN 托管（gzip/brotli）、图片懒加载、导出图片（html2canvas）性能优化、路由/组件按需加载（代码分割）。

**数据模型**：无新增业务表；新增 yml 配置 `search.engine`、`storage.object`、`mq.*`（均支持关闭降级）。

**验收标准**
- `search.engine=elasticsearch` 时简历/社区/模板搜索支持分词与相关性排序，且 ES 挂掉可降级 `LIKE`。
- 头像 / 封面 / 缩略图可上传并返回可访问 URL。
- 埋点 / AI / 聚合 / ES 同步异步化后，主链路响应不受阻塞，失败可重试。
- 公开接口与 AI 接口限流生效，热点数据命中多级缓存。

**难点**：ES 与 MySQL 数据一致性（最终一致 + 补偿）；ES 数据隔离；OSS 权限与成本；MQ 引入的运维复杂度（可用线程池起步逐步演进）。

---

## 5. 变更清单汇总

### 5.1 新增表（挂 `user_id` 隔离；公开对象由业务隔离）
- R1：`resume_share`
- R2：`access_log`、`stat_minute`
- R3：`community_post`、`community_like`、`community_collect`、`community_comment`、`community_report`
- R4：`template`
- R5：`interview_set`、`ai_kb_entry`（RAG 知识库，R5/R6 共用）
- R6：`ai_generation_log`
- R9：复用 `access_log`/`stat_minute`/`community_*`/`resume_share`，无新表

### 5.2 新增基建
- **AI 能力层**（Spring AI：`ChatClient` + `EmbeddingModel` + `VectorStore`；RAG + Skill），R5/R6/R8 复用。
- **埋点/聚合链路**（分钟级，带 `resume_id`），R2/R9/R3 复用。
- **免登录公开读取 + 隐私脱敏**（R1，R3 复用）。
- **规则引擎**（`AuditRuleEngine`，社区内容审核）。
- **短链域名/分享配置**（`ShareProperties`）、**AI 配置**（`AiProperties` + `RagProperties`）。
- **检索层抽象**（`SearchService` ES/MySQL 双实现，可开关），R10-O1。
- **对象存储抽象**（`ObjectStorageService`），R10-O2。
- **异步任务**（消息队列抽象 `MessageQueue`：Redis Stream 起步，后续兼容 RabbitMQ/Kafka；无 MQ 时线程池兜底 `AsyncTaskService`），R10-O3。
- **多级缓存与限流**（Caffeine + Redis + 令牌桶），R10-O4。

### 5.3 主要后端新增 Controller
`ShareController`、`CommunityController`、`AiController`、`TemplateController`、`MyStatsController`（R9）、`StatsController`（增强 `AdminService`）、`AuditRuleEngine` 组件；新增 `SearchService`、`ObjectStorageService`、`AsyncTaskService`（R10）。

### 5.4 主要前端新增
- 用户端：分享弹窗、`/s/:key` 只读页、社区列表/详情、我的数据中心（R9）、AI 面试/简历助手、模板列表切换（R4）。
- 管理端：图表化看板、社区审核、模板管理。

---

## 6. 实现顺序（分阶段里程碑）

**阶段一（低风险、见成效）**
- [ ] R1 定点分享；R2 报表（分钟级）；R9 用户数据后台；R4 模板表 + 三套内置。

**阶段二（内容与变现）**
- [ ] R3 社区（规则引擎先审后发 + 高热度再审，复用 R1 渲染、R2 埋点）。

**阶段三（AI 增值，DeepSeek）**
- [ ] 落地 AI 网关（DeepSeek）。
- [ ] R5 面试题生成 → R6 AI 修改/完善简历。

**阶段四（生态与壁垒）**
- [ ] R8：模板可视化绘制 + 市场（承接 R4）；其余亮点候选按需。

**跨阶段优化项（R10，按需穿插）**
- [ ] O4 缓存限流 + O5 慢查询索引（阶段一即可快赢）。
- [ ] O2 对象存储（阶段二配套社区封面 / 模板缩略图）。
- [ ] O3 消息队列异步化（阶段三配套 AI / 埋点 / 聚合）。
- [ ] O1 Elasticsearch 全文检索（待搜索体验与数据规模诉求明确后落地）。

---

## 7. 风险与依赖

1. **外部服务**：R5/R6/R8（Spring AI，默认 DeepSeek，另需 embedding 模型）存在第三方接口/资质/配额/合规风险，供应商可插拔并预留降级（`ai.enabled=false` 明确报错）。
2. **合规**：AI 处理简历含个人信息，需告知授权、脱敏最小化；需符合个人信息保护法规。
3. **高并发一致性**：社区点赞/收藏计数需幂等与并发安全（Redis 计数 + 回写 / 乐观锁）。
4. **安全红线**：模板渲染引擎与富文本输出防 XSS；公开分享/社区默认脱敏联系方式；规则引擎需持续可配。
5. **成本控制**：AI 调用配额、限流、token 计费、超时熔断。
6. **范围蔓延**：模板绘制（R8-A1）工程量大，当前 R4 仅建表 + 内置三套，可视化编辑器另立阶段。
7. **检索与一致性**：ES 与 MySQL 数据一致性需「最终一致 + 补偿」；ES/OSS/MQ 引入额外运维与成本，须以「可开关 + 可降级」方式落地。

---

## 8. 待确认决策点

- **模板市场**：用户模板内容版权与审核策略、市场冷启动（内置模板打底）。
- **社区规则引擎**：敏感词库来源与初始规模、高热度异常阈值（浏览量/点赞/举报）具体数值。
- **分享**：短链是否需要自定义短域名证书/备案、二维码是否默认生成。
- **用户数据后台**：报表时间窗（近 7/30 天）与指标是否含「分享访问」。
- **AI**：DeepSeek `model` 版本（`deepseek-chat`/`deepseek-reasoner`）、embedding 模型选型（远程 OpenAI 兼容 or 本地 BGE）、RAG 向量库选型、是否开放流式、单用户每日生成次数上限。
- **模板**：R4 阶段是否将 `resume.template_id` 改为存 `template.code`（建议是），迁移三套旧数据的兼容方式。
- **检索/存储**：ES 是否必上（建议 `search.engine=mysql` 兜底，按需切 `elasticsearch`）；OSS 选型（阿里云 / MinIO）与直传策略；MQ 先以 **Redis Stream** 落地，后续按需切 RabbitMQ / Kafka（走 `MessageQueue` 抽象）。

---

## 附录：错误码沿用

所有新增接口继续使用统一响应体与既有错误码：`0 成功 / 400 参数 / 401 未认证或账号被踢 / 403 无权限或禁用 / 404 不存在 / 409 冲突或版本冲突 / 500 服务器错误`，AI/社区失败场景优先映射至既有错误码，不新增多余语义。