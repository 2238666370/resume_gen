# Spec：简历生成器两项增强需求

> 本文档仅描述需求与设计，不涉及具体实现。实现前请评审并确认文末「待确认决策点」。

## 1. 范围

| 编号 | 需求 | 涉及端 |
|------|------|--------|
| R2 | 管理后台增强：角色管理、用户状态修改即时踢下线、按用户查看其简历 | 后端 + 管理端 |
| R3 | 用户端：登录/注册加图片验证码、登录后进入个人主页、编辑器增加返回主页按钮 | 后端 + 用户端 |

> 备注：原「栏目生命周期 / 逻辑删除」需求（R1）本轮**维持原样，不修改**。

---

## 2. 现状梳理

### 2.1 数据模型要点（`resume-server/src/main/resources/db/schema.sql`）

- `sys_user`：含 `role`（USER/ADMIN）、`status`（1 正常 / 0 禁用）、`token_version`（单会话权威源）。

### 2.2 认证（`AuthService` / `AuthController`）

- `register` / `login`：无验证码校验。`login` 成功后 `userMapper.incrementTokenVersion` + `sessionService.invalidate` 实现单会话踢下线。

### 2.3 用户端（`resume_gen`）

- 路由：`/login`、`/`（`ResumeListPage` = 「我的简历」列表）、`/editor/:id`（`EditorPage`）。
- `Toolbar`（编辑器顶部）**无「返回主页」按钮**；`EditorPage` 仅在错误态提供「返回列表」。
- `api/auth.ts`：`register(username, password, email?)`、`login(username, password)`，均无验证码参数。

### 2.4 管理端（`admin-ui`）

- 路由：`/`（看板）、`/users`、`/resumes`。
- `UsersPage`：仅用户名/昵称/邮箱/角色展示/状态 Switch；**无角色编辑、无「查看用户简历」**。
- `AdminService`：`listUsers` / `setUserStatus` / `listAllResumes` / `deleteResume` / `stats`；**无角色修改、无按用户查简历、`setUserStatus` 不踢下线**。

---

## 3. R2：管理后台增强

### 3.1 后端

| 接口 | 方法 | 说明 |
|------|------|------|
| `PUT /admin/users/{id}/role` | 修改角色 | 请求体 `{ role: "USER" | "ADMIN" }`，校验角色值白名单 |
| `PUT /admin/users/{id}/status` | 增强 | 禁用（`status=0`）时**立即踢下线**：`incrementTokenVersion(id)` + `sessionService.invalidate(id)` |
| `GET /admin/users/{id}/resumes` | 新增 | 返回该用户简历分页列表（`user_id` 过滤 + 分页） |

- `AdminService` 注入 `SessionService`。
- `setUserStatus` 在 `status=0` 时执行踢下线；新增 `updateUserRole(UserId, role)`、`listUserResumes(userId, page, size)`。
- 校验：`role` 取值白名单；目标用户不存在返回 404。是否禁止修改自身（防自锁）见文末待确认。

### 3.2 管理端（`admin-ui`）

- `UsersPage`：
  - 角色列改为可编辑（`Select` 切换 USER/ADMIN，ADMIN 变更需二次确认）。
  - 状态 `Switch` 变更后提示「已禁用并强制下线」。
  - 新增「查看简历」操作 → 抽屉/弹窗展示该用户简历列表（标题/模板/更新时间），支持删除。
- `api.ts` 新增 `setUserRole(id, role)`、`userResumes(id, page, size)` 两个封装。

### 3.3 验收标准（R2）

- 管理员可将用户角色在 USER/ADMIN 间切换，接口与 UI 同步生效。
- 禁用用户后，该用户已登录的 Token 立即失效（后续请求返回 401 被踢下线）。
- 点开用户可查看并分页浏览其名下简历。

---

## 4. R3：用户端（验证码 / 个人主页 / 返回按钮）

### 4.1 图片验证码

- **后端**：
  - 新增 `GET /api/auth/captcha`，返回 `{ captchaId, imageBase64 }`（`data:image/png;base64,...`）。
  - `login` / `register` 请求体新增 `captchaId`、`captchaCode`；后端校验（大小写不敏感、一次性消费、过期失效），校验失败返回 400 错误码。
  - 验证码字符生成用 JDK `java.awt`（不引入第三方依赖）；存储默认**内存缓存（ConcurrentHashMap + TTL）**，可选 Redis 实现（对齐「可选 Redis」的既有设计）。
- **用户端**：
  - `LoginPage` 登录/注册均展示验证码图片 + 输入框，点击图片刷新。
  - `api/auth.ts` 新增 `getCaptcha()`；`login` / `register` 传 `captchaId`/`captchaCode`。

### 4.2 个人主页与返回按钮

- 将 `ResumeListPage` 升级为「个人主页」：顶部展示当前用户信息（昵称/用户名）、退出登录，主体呈现该用户名下简历列表（现状已具备，补充用户信息区）。
- `EditorPage` 的 `Toolbar` 最左侧新增「返回主页」按钮，`navigate('/')`；错误态「返回列表」文案统一为「返回主页」。
- 路由保持 `/login` → `/`（主页）→ `/editor/:id`。

### 4.3 验收标准（R3）

- 登录与注册均需正确输入验证码；验证码错误被拒绝且图片可刷新。
- 登录后落在个人主页，展示当前用户信息与其简历列表。
- 编辑器内可一键返回主页。

---

## 5. 变更清单汇总

### 5.1 后端

- 新增 `CaptchaService` / `AuthController` 验证码接口（R3）。
- `AuthService` / DTO：登录注册校验验证码字段（R3）。
- `AdminService` / `AdminController`：角色修改、按用户查简历、禁用踢下线（R2）。

### 5.2 用户端

- `api/auth.ts`、`LoginPage`（验证码，R3）。
- `ResumeListPage`（个人主页，R3）、`Toolbar` / `EditorPage`（返回主页，R3）。

### 5.3 管理端

- `admin-ui/src/api.ts`、`UsersPage.tsx`（角色编辑、禁用提示、查看简历抽屉）。

---

## 6. 待确认决策点

- 验证码存储：默认内存（单实例），是否需同时支持 Redis 实现。
- 验证码失效/一次性语义：登录、注册是否共用同一验证码源与过期时间（建议共用，TTL 60s，一次性消费）。
- R2 是否禁止管理员修改自己的角色 / 状态（防自锁）。
- R2 角色切换与禁用是否需要在操作前二次确认（建议 ADMIN 变更与禁用需确认）。