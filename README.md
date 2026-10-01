# 简历生成器 Resume Generator

一个**前后端分离**的在线简历平台：多模板简历编辑与导出、定点分享、简历社区、模板市场、AI 简历优化与面试题库、用户数据看板与运营管理后台。

- **用户端 Web**：React 19 + TypeScript + Vite 5 + Tailwind CSS + Zustand
- **管理端 Web**：React 19 + TypeScript + Vite 5 + Ant Design 5 + Zustand
- **后端服务**：Java 17 + Spring Boot 3.2.5 + MyBatis-Plus 3.5.5 + MySQL 8（Redis 可选）+ JJWT + Spring AI 1.0.0-M5

---

## 目录

1. [架构设计](#1-架构设计)
2. [功能实现](#2-功能实现)
3. [目录结构](#3-目录结构)
4. [部署环境](#4-部署环境)
5. [配置设计](#5-配置设计)
6. [数据模型](#6-数据模型)
7. [接口概览](#7-接口概览)
8. [测试](#8-测试)
9. [技术亮点](#9-技术亮点)
10. [常见问题](#10-常见问题)

---

## 1. 架构设计

### 1.1 总体形态

三端分离：用户端 Web、管理端 Web、独立 API 服务。所有端通过统一的 HTTP/JSON 协议通信，响应体统一为 `{ code, message, data }`。

```
┌────────────────┐        ┌────────────────┐
│   用户端 Web    │        │   管理端 Web    │
│  localhost:5173│        │  localhost:3000│
└───────┬────────┘        └───────┬────────┘
        │  /api/**  (JWT)          │  /admin/**  (JWT + ADMIN)
        └───────────┬──────────────┘
                    ▼
      ┌──────────────────────────────────┐
      │      resume-server :8081         │
      │  Controller → Service → Mapper   │
      └───┬──────────┬──────────┬────────┘
          │          │          │
     ┌────▼───┐ ┌────▼────┐ ┌───▼────────────┐
     │ MySQL  │ │  Redis  │ │ Elasticsearch  │
     │(必选)  │ │(可选)   │ │(可选，可降级)   │
     └────────┘ └─────────┘ └────────────────┘
                    │
              ┌─────▼──────┐
              │  DeepSeek  │  （Spring AI / OpenAI 兼容协议）
              └────────────┘
```

### 1.2 后端分层

```
controller  ──  仅做参数校验与协议适配，不写业务
service     ──  业务编排（含事务、缓存、AI、MQ 调用）
repository  ──  简历存储抽象（mysql | json 双实现）
mapper      ──  MyBatis-Plus 数据访问
entity/dto  ──  实体与传输对象（DTO/VO 分离）
security    ──  JWT 签发校验、拦截器、单会话、用户上下文
common      ──  统一响应体、错误码、全局异常、分页
```

### 1.3 五个「可切换」抽象层（条件装配）

后端所有外部依赖都通过接口 + `@ConditionalOnProperty` 抽象出双实现，**配置即切换、缺失即降级**：

| 抽象层 | 接口 | 实现 | 开关 |
|--------|------|------|------|
| 简历存储 | `ResumeRepository` | `MysqlResumeRepository` / `JsonResumeRepository` | `resume.storage.type` = `mysql` / `json` |
| 缓存 | `CacheService` | `RedisCacheService` / `NoopCacheService` | `resume.cache.type` = `redis` / `none` |
| 消息队列 | `MessageQueue` | `RedisStreamMessageQueue` / `InMemoryMessageQueue` | `mq.type` = `stream` / `memory`（预留 kafka、rabbitmq） |
| 全文检索 | `SearchService` | `ElasticsearchSearchService` / `MysqlSearchService` | `search.engine` = `elasticsearch` / `mysql` |
| AI 任务存储 | `AiTaskStore` | `RedisAiTaskStore` / `InMemoryAiTaskStore` | 随 `resume.cache.type` |

> 设计价值：**开发环境零外部依赖也能跑通全链路**（`json` + `none` + `memory` + `mysql` 检索 + `AI_ENABLED=false`），生产环境再逐项打开。

### 1.4 前端架构（用户端）

- **路由**：HashRouter，`React.lazy` 路由级懒加载 + `manualChunks` 依赖分包。
- **信息架构（F1）**：4 个一级分区 + 页内二级 Tab，Tab 由 `?tab=` 查询参数驱动（可分享、可前进后退、刷新保持）。

```
工作台 /         → 我的简历 | 我的分享 | 我的模板
AI 助手 /ai       → 面试准备 | 生成记录
发现 /discover    → 社区 | 模板市场
我的 /me          → 数据中心 | 个人信息
```

- **状态**：Zustand（`authStore` 认证态 + `resumeStore` 编辑器数据）。
- **网络**：axios 实例统一注入 `Authorization`、统一解包 `{code,message,data}`、401 自动跳登录、`getErrorMessage` 统一错误码文案。
- **埋点**：`RouteTracker` 按一级路径上报 PV/UV，旧路由重定向路径不计入，避免重复计数。

---

## 2. 功能实现

### 2.1 用户端

| 模块 | 能力 |
|------|------|
| **认证** | 注册（注册即登录）、登录、登出、图片验证码、当前用户信息 |
| **简历编辑** | 个人信息 / 工作 / 实习 / 教育 / 技能 / 项目 / 证书 / 语言 / 自定义板块；拖拽排序、显隐开关；`**加粗**` 富文本；主题配色（预设 + 取色器）；800ms 防抖自动保存（乐观锁） |
| **模板** | 4 套内置模板（经典 / 现代 / 简约 / 自定义 Schema）；模板市场浏览、预览、一键套用；「我的模板」管理与发布 |
| **版本时光机** | 自动留存快照、历史列表、版本 diff、一键回滚 |
| **导出** | PNG（html2canvas 2×）、PDF（iframe 预览 + 浏览器原生打印）、JSON 导入导出 |
| **定点分享** | 生成短链/分享 key、有效期、撤销、免登录查看、SEO/OG 社交卡片 |
| **社区** | 信息流（最新/最热）、标签、搜索、发布、点赞、收藏、评论、举报；「我的」发布/点赞/收藏 |
| **AI** | 简历全文改稿（diff 逐条接受）、润色、扩写、建议、简历评分 + JD 匹配度、面试题库生成；异步 + SSE 推送、历史记录 |
| **水印** | 导出图片叠加可视水印 + LSB 盲水印（像素级溯源） |
| **数据** | 数据中心：观阅 PV/UV 趋势、获赞/收藏/评论、按简历明细 |

### 2.2 管理端

数据看板（分钟级 PV/UV 曲线）、用户管理（搜索 / 角色调整 / 启用禁用即时踢下线 / 查看其简历）、简历管理、社区审核（帖子 / 举报 / 复审）、模板审核、AI 知识库维护、水印配置。

### 2.3 后端关键机制

- **统一响应与错误码**：`{code,message,data}`；错误码 `400/401/403/404/409/429/500`，401 区分「登录失效」与「被踢下线」。
- **用户数据隔离**：JWT 解析 `user_id`，所有明细表冗余 `user_id` 列并建索引，查询直接 `WHERE user_id = ?`，跨用户访问返回 404/403（防 IDOR）。
- **单会话登录**：`sys_user.token_version` 为唯一权威源（Redis 仅加速），新登录原子递增 `token_version` 使旧 Token 立即失效。
- **乐观锁**：`resume.version` 处理并发/自动保存冲突，冲突返回 409。
- **AI 治理**：超时熔断（CLOSED→OPEN→HALF_OPEN）、单用户频控、双队列隔离（高/低成本）、ReAct 多轮推理、RAG 检索增强，全部可开关降级。
- **消息可靠性**：Redis Stream 消费者组 + ACK + PEL 重投（XCLAIM）+ 重试上限转死信队列；消费端幂等守卫。
- **社区计数写扩散**：读模型（`community_post` 计数列）+ 事实模型（明细表唯一键），写时聚合、读时直读；Redis Hash 计数读模型 24h TTL 自愈，缺失回源 DB。

---

## 3. 目录结构

```
resume_gen/
├── src/                          # 用户端前端
│   ├── api/                      # axios 客户端 + 各域接口封装
│   ├── components/               # 通用组件 / 编辑器面板 / 模板渲染 / 布局壳
│   │   ├── layout/               # AppShell（一级导航）、NavTabs（二级 Tab）
│   │   ├── ui/                   # DropdownMenu 等自研轻组件
│   │   ├── editor/               # 各模块表单编辑器
│   │   └── templates/            # 内置模板 + 自定义 Schema 渲染器
│   ├── pages/                    # 路由页面（工作台/AI/发现/我的/编辑器/分享/登录）
│   ├── store/                    # Zustand（authStore / resumeStore）
│   ├── utils/                    # 导出、diff、文本渲染
│   ├── types/                    # 类型定义
│   └── test/                     # 测试初始化（jest-dom）
├── admin-ui/                     # 管理端前端（Ant Design）
├── resume-server/                # Spring Boot 后端
│   └── src/main/java/com/resumegen/
│       ├── controller/  service/  mapper/  entity/  dto/
│       ├── repository/           # 存储抽象（mysql / json）
│       ├── cache/                # 缓存 + 社区计数读模型抽象
│       ├── mq/                   # 消息队列抽象（stream / memory）
│       ├── search/               # 检索抽象（elasticsearch / mysql）
│       ├── ai/                   # AI 能力层（ReAct 引擎、Skill、熔断、限流、任务存储）
│       ├── security/  common/  config/  rule/
│       └── resources/
│           ├── application.yml / -dev.yml / -prod.yml
│           └── db/schema.sql     # 24 张表建表脚本
├── spec/                         # 各需求（R1~R10、F1）规格文档
├── requirements.md               # 需求总纲
├── plan.md                       # 前后端分离改造规划
├── spec.md                       # 早期增强需求
├── 手搓本项目指引.md              # 从 0 到 1 复刻本项目
└── README.md
```

---

## 4. 部署环境

### 4.1 前置要求

| 组件 | 版本 | 是否必需 |
|------|------|----------|
| JDK | 17+ | 必需 |
| Maven | 3.9+ | 必需 |
| Node.js | 18+（推荐 22.x） | 必需 |
| MySQL | 8.x | `resume.storage.type=mysql` 时必需 |
| Redis | 5.0+ | 仅 `mq.type=stream` 或 `resume.cache.type=redis` 时 |
| Elasticsearch | 7.x + IK 分词 | 可选（缺省自动降级 MySQL LIKE） |

### 4.2 初始化数据库

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS resume_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p resume_db < resume-server/src/main/resources/db/schema.sql
```

首次启动时 `DataInitializer` 会自动创建默认管理员并写入内置模板。

| 账号 | 密码 | 说明 |
|------|------|------|
| `admin` | `admin123` | 请部署后立即修改 |

### 4.3 启动后端

```bash
# 按需修改 resume-server/src/main/resources/application-dev.yml 的数据源账号密码
cd resume-server
mvn spring-boot:run
```

监听 **http://localhost:8081**，OpenAPI 文档 `/swagger-ui.html`。

> **零依赖运行**：将 `resume.storage.type` 改为 `json`、`resume.cache.type` 改为 `none`，即可不装 MySQL/Redis 跑通简历 CRUD。

### 4.4 启动用户端

```bash
npm install
npm run dev        # http://localhost:5173
```

后端地址通过环境变量覆盖（默认 `http://localhost:8081`）：

```bash
# .env.local
VITE_API_BASE_URL=http://localhost:8081
VITE_AI_ASYNC_ENABLED=true   # 与后端 ai.async.enabled 同时为 true 才走「异步 + SSE」
```

### 4.5 启动管理端

```bash
cd admin-ui
npm install
npm run dev        # http://localhost:3000（已内置代理 /api、/admin → 8081）
```

### 4.6 生产部署

1. 构建产物

   ```bash
   npm run build                    # 用户端 → dist/
   cd admin-ui && npm run build     # 管理端 → admin-ui/dist/
   cd resume-server && mvn clean package -DskipTests   # 后端 → target/*.jar
   ```

2. 以 `prod` 环境启动后端（密钥全部走环境变量）

   ```bash
   java -jar resume-server.jar --spring.profiles.active=prod
   ```

3. 环境变量清单（`application-prod.yml` 引用）

   | 变量 | 用途 |
   |------|------|
   | `DB_USER` / `DB_PASSWORD` | 数据库账号密码 |
   | `JWT_SECRET` | JWT 签名密钥（≥32 位随机串） |
   | `REDIS_HOST` / `REDIS_PASSWORD` | Redis 连接 |
   | `AI_API_KEY` | 大模型 API Key |
   | `AI_ENABLED` / `RAG_ENABLED` | AI / RAG 开关 |
   | `MQ_TYPE` | `stream` / `memory` |
   | `SEARCH_ENGINE` / `ES_URL` | 检索后端与 ES 地址 |

4. 将 `dist/`、`admin-ui/dist/` 交给 Nginx 托管，并把 `/api`、`/admin` 反向代理到后端端口。

> ⚠️ **安全提示**：`application-dev.yml` 中直接写有开发用数据库密码与 AI Key，**仅限本地开发**。对外部署务必使用 `prod` profile + 环境变量注入，并轮换已泄露的密钥。

---

## 5. 配置设计

配置采用「主配置 + 环境覆盖」三文件叠加载入：

| 文件 | 作用 |
|------|------|
| `application.yml` | 主配置：端口、数据源默认值、MyBatis-Plus、全部业务默认值 |
| `application-dev.yml` | 开发环境（默认激活）：本地账号密码、AI Key、存储/缓存选择 |
| `application-prod.yml` | 生产环境：明文替换为 `${ENV}` 占位符 |

### 5.1 存储与缓存

```yaml
resume:
  storage:
    type: mysql                # mysql | json
    json-path: ./data/resumes  # json 模式数据目录
  cache:
    type: none                 # redis | none
    ttl-seconds: 3600
    prefix: "resume:"
  cors:
    allowed-origins: "http://localhost:5173,http://localhost:3000"
```

| 配置项 | 取值 | 说明 |
|--------|------|------|
| `resume.storage.type` | `mysql` / `json` | `json` 模式无需数据库即可运行 |
| `resume.cache.type` | `redis` / `none` | `none` 时不依赖 Redis |
| `resume.cors.allowed-origins` | 逗号分隔 | 用户端/管理端域名白名单 |

### 5.2 分享 / SEO / 水印

```yaml
resume:
  share:
    base-url: http://localhost:5173   # 短链域名
    default-ttl-days: 30              # 0 = 永久
    seo:
      enabled: true
      og-image-default: ""
  watermark:
    enabled: true
    visible: { text: "{user}_{date}", opacity: 0.08, density: medium }
    blind:   { enabled: true, payload: "user_id|device_id" }
```

### 5.3 社区治理

```yaml
resume:
  community:
    sensitive-words: [广告, 刷单, 赌博, 博彩, 色情, 诈骗, 代开发票, 办证]
    report-review-threshold: 5      # 举报数 → 人工复审
    like-review-threshold: 1000     # 点赞数 → 热帖复审
    view-review-threshold: 10000    # 浏览数 → 热帖复审
  public-rate-limit:
    enabled: false                  # 公开接口限流开关
    limit: 60
    window-seconds: 60
```

### 5.4 消息队列

```yaml
mq:
  type: ${MQ_TYPE:memory}   # stream | kafka | rabbitmq | memory
  redis:
    stream-prefix: "mq:stream:"
    group-prefix: "mq:group:"
    retry-max: 3            # 重试上限，超限转死信
    claim-idle-ms: 30000    # 卡死判定阈值（XCLAIM 重投）
    dead-letter: true
```

> `stream` 需 Redis 5.0+；旧版 Redis 会自动降级为 `memory`（进程内队列）。接入 Kafka/RabbitMQ 只需新增一个 `MessageQueue` 实现类，业务代码零改动。

### 5.5 AI 能力层

```yaml
ai:
  enabled: ${AI_ENABLED:true}      # 总开关，false 时接口走降级
  provider: deepseek
  timeout-seconds: 60              # 超时熔断
  circuit-breaker: { enabled: true, failure-threshold: 5, open-seconds: 30 }
  rate-limit:
    enabled: ${AI_RATE_LIMIT_ENABLED:false}
    window-seconds: 60
    limit: 20
    per-type: { interview.generate: 3, resume.score: 10 }
  react:
    enabled: ${AI_REACT_ENABLED:true}
    max-iterations: 4
  async:
    enabled: ${AI_ASYNC_ENABLED:true}
    high: { concurrency: 8 }       # 高成本队列
    low:  { concurrency: 3 }       # 低成本队列

rag:
  enabled: ${RAG_ENABLED:false}
  store: { type: ${RAG_STORE_TYPE:memory} }   # memory | redis | pgvector
  top-k: 5
  min-score: 0.65

spring:
  ai:
    openai:
      base-url: ${AI_BASE_URL:https://api.deepseek.com}
      api-key: ${AI_API_KEY:}      # 环境变量注入
      chat:      { enabled: ${AI_ENABLED:true}, options: { model: ${AI_CHAT_MODEL:deepseek-flash} } }
      embedding: { enabled: ${RAG_ENABLED:false} }
```

### 5.6 埋点与检索

```yaml
stats:
  async: { enabled: true }         # 埋点异步化（false 时同步直写）

search:
  engine: ${SEARCH_ENGINE:elasticsearch}   # mysql | elasticsearch
  elasticsearch:
    url: ${ES_URL:http://localhost:9200}
    index-prefix: resume_gen_
    auto-create-index: true        # 启动自动建索引 + IK 分词 mapping
```

### 5.7 JWT 与服务器

```yaml
server: { port: 8081 }
jwt:
  secret: "please-change-me-to-a-long-random-secret-at-least-32-chars"
  expire-seconds: 86400
  header: Authorization
  token-prefix: "Bearer "
```

---

## 6. 数据模型

MySQL 模式共 **24 张表**（见 `db/schema.sql`），分为六类：

| 分类 | 表 |
|------|-----|
| 用户与认证 | `sys_user`（含 `role` / `status` / `token_version`） |
| 简历主体 | `resume`（含 `version` 乐观锁）、`resume_snapshot`（版本时光机） |
| 简历明细 | `resume_personal`、`resume_education`、`resume_experience`（`item_type` 区分工作/实习）、`resume_skill`、`resume_project`、`resume_certificate`、`resume_language`、`resume_custom_section`、`resume_section` |
| 分享 | `resume_share` |
| 社区 | `community_post`（读模型计数列）、`community_like`、`community_collect`、`community_comment`、`community_report` |
| 模板 / AI / 统计 | `template`、`ai_kb_entry`、`interview_set`、`ai_generation_log`、`access_log`、`stat_minute` |

设计要点：

- 所有明细表冗余 `user_id` 并建索引，直接支撑数据隔离。
- 社区计数采用「读模型 + 事实模型」写扩散方案，明细表唯一键兜底防重复。
- 统计分「原始埋点 `access_log`」与「分钟级预聚合 `stat_minute`」两层，查询走预聚合。
- `ai_generation_log` 兼作 AI 产出持久化与历史查询。

---

## 7. 接口概览

统一响应体：`{ "code": 0, "message": "success", "data": ... }`

| 模块 | 前缀 | 鉴权 | 代表接口 |
|------|------|------|----------|
| 认证 | `/api/auth` | 部分匿名 | `POST /register`、`POST /login`、`POST /logout`、`GET /me`、`GET /captcha` |
| 个人信息 | `/api/profile` | 登录 | `GET /`、`PUT /` |
| 简历 | `/api/resumes` | 登录 | CRUD、`GET /{id}/export.json`、`POST /import`、`GET /{id}/history`、`GET /{id}/diff`、`POST /{id}/rollback/{snapshotId}` |
| 模板 | `/api/templates` | 部分匿名 | 列表、市场、我的、套用、发布、增删改 |
| 分享 | `/api/shares` | 登录 | 创建、列表、撤销 |
| 社区 | `/api/community` | 部分匿名 | 信息流、详情、点赞/收藏/评论/举报、我的 |
| 我的数据 | `/api/stats/my` | 登录 | 概览、按简历统计、趋势、评论 |
| 埋点 | `/api/public/track`、`/api/stats/track` | 匿名 / 登录 | 页面访问上报 |
| AI 简历 | `/api/ai/resume` | 登录 | `rewrite`、`expand`、`suggest`、`improve`、`score`（均含 `/async`） |
| AI 面试 | `/api/ai/interview` | 登录 | `generate`、`/async`、列表、重命名、删除 |
| AI 任务 | `/api/ai/task` | 登录 | `GET /{taskId}` 轮询、`GET /{taskId}/stream` SSE |
| AI 历史 | `/api/ai/history` | 登录 | 列表（按类型筛选）、详情 |
| 分享/匿名 | `/api/public/**` | 匿名 | 分享页查看、OG 元信息 |
| 管理端 | `/admin/**` | ADMIN | 用户（角色/状态/其简历）、简历、模板审核、社区审核、知识库、水印配置、统计概览 |

---

## 8. 测试

### 前端（Vitest + Testing Library）

```bash
npm test
```

覆盖：简历 diff 引擎（逐字段对齐 / 数组整段 / 逐条接受合并）、统一错误码映射、`resumeStore`（明细增删改 / 板块管理 / 数据装载）、`authStore`（持久化）、`DropdownMenu` / `NavTabs` / `ProgressBar` 组件交互。**51 个用例**。

### 后端（JUnit 5 + MockMvc）

```bash
cd resume-server && mvn test
```

**269 个用例**，覆盖 JWT 与单会话、缓存双实现、认证与鉴权、简历 CRUD 与乐观锁、双存储聚合读写、分享、社区（含计数写扩散）、模板、快照、统计、AI（ReAct / 熔断 / 限流 / 任务存储）、MQ（内存 / Redis Stream）、检索（ES / MySQL）、水印、规则引擎等。测试不依赖真实 MySQL/Redis/ES。

---

## 9. 技术亮点

1. **五个可切换抽象层**（存储 / 缓存 / MQ / 检索 / AI 任务）——接口 + 条件装配，配置即切换、缺失即降级，开发可零外部依赖运行。
2. **消息队列抽象**：Redis Stream 消费者组 + ACK + PEL + XCLAIM 重投 + 重试上限转死信 + 消费幂等；换 Kafka/RabbitMQ 只加实现类，业务无感。
3. **AI 能力层治理**：Spring AI 接入 DeepSeek；「预规划 + ReAct」多轮推理；RAG 知识库检索增强；`@Tool` 技能编排；超时熔断状态机 + 单用户频控 + 双队列资源隔离 + 异步 SSE 推送——全部可开关降级。
4. **AI 产出可控落库**：LLM 输出经前端「客户端 diff + 逐条接受/拒绝」合并，规避模型偶发不合规字段污染数据。
5. **社区计数写扩散**：读模型 + 事实模型分离，写时聚合、读时直读，Redis Hash 计数 24h TTL 自愈，最终一致且高并发友好。
6. **安全纵深**：JWT + `token_version` 单会话踢下线；明细表冗余 `user_id` 强制隔离防 IDOR；富文本 XSS 片段拒绝、颜色/日期正则白名单；Schema 渲染引擎沙箱化。
7. **性能工程**：ES + IK 中文分词全文检索（不可用自动降级 LIKE）、分钟级预聚合统计、派生表 LEFT JOIN 消除慢查询、路由懒加载 + 依赖分包、Caffeine 化零依赖缓存。
8. **水印防爬**：可视水印 + LSB 像素级盲水印（MAGIC + 长度 + payload 协议），导出即可溯源。
9. **信息架构重构**：按用户心智重组为 4 个一级分区 + URL 驱动的二级 Tab，旧路由 `<Navigate replace>` 兼容重定向。

---

## 10. 常见问题

**Q：没有 MySQL / Redis 能跑吗？**
A：能。`resume.storage.type=json` + `resume.cache.type=none` + `mq.type=memory` 即可零外部依赖运行。

**Q：AI 接口报错或很慢？**
A：设 `AI_ENABLED=false` 走降级；或调小 `ai.timeout-seconds`、打开 `ai.rate-limit.enabled` 保护后端。

**Q：能读到别人的简历吗？**
A：不能。所有查询强制 `WHERE user_id = ?`，越权返回 404/403。

**Q：同一账号多地登录会怎样？**
A：单会话策略，新登录递增 `token_version`，旧 Token 立即失效并返回 401「账号已在其他设备登录」。

**Q：保存提示「数据冲突」？**
A：触发乐观锁（409），刷新页面后重试。

**Q：搜索没走 Elasticsearch？**
A：检查 `search.engine` 与 `ES_URL`；ES 不可用时自动降级为 MySQL LIKE，不影响功能。
