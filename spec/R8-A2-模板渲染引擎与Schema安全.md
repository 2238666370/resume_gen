# R8-A2 模板渲染引擎 + Schema 安全（预览 / 导出统一）

> 来源：`requirements.md` §4（R8 候选 A 拆分）\`R4-模板数据化.md\` §7「渲染引擎抽象」
> 阶段：阶段四 | 难度：⭐⭐⭐⭐⭐ 高 | 依赖：R4 模板表、R8-A1 Schema 模型

## 1. 定位

抽象一个**统一模板渲染引擎**，将「官方三套硬编码组件 + 用户自定义 Schema 模板」统一为「Schema 驱动 + 安全沙箱」的渲染入口。它向上支撑：(1) 编辑器实时预览（R8-A1）、(2) 简历编辑页的模板切换渲染、(3) 导出图片 / PDF、以及分享 / 社区只读渲染（R1/R3）。同时把「Schema 安全」作为渲染的前置强制关卡，杜绝数据驱动 UI 带来的存储型 XSS。

## 2. 功能点

1. **渲染引擎抽象**：定义 `TemplateRenderer` 接口：
   - `OfficialRenderer`：现有三套硬编码组件（classic / modern / minimal，以 `template.code` 映射，兼容存量）。
   - `SchemaRenderer`：消费 `template.schema`（R8-A1 定义）动态渲染通用模板。
   - 按 `template.schema == null` 走 `OfficialRenderer`，否则走 `SchemaRenderer`。
2. **Schema 渲染**：解析 `schema.sections` → 组件树 → 绑定 `ResumeDTO` 数据 → 输出统一 DOM（含语义 class，供导出复用）。官方三套可逐步迁移为 Schema（`schema` 回填后自动切 `SchemaRenderer`）。
3. **Schema 安全（强制）**：
   - **白名单组件类型**：仅允许 `container/list/text/richtext/image/date-range` 等预定义类型。
   - **白名单数据路径**：`bind` 仅允许 `ResumeDTO` 合法字段路径，禁止任意表达式 / `eval`。
   - **白名单样式键**：仅允许安全 CSS 属性键；拒绝 `expression`、`url()`、`@import`、`position:fixed` 等危险值。
   - **富文本清洗**：区块 `type=richtext` 渲染前经既有 XSS 过滤（拒绝 `<script>/<iframe>/<svg>/javascript:` 等，与简历端一致）。
   - **沙箱渲染**：编辑器预览 / 不可信模板渲染统一放入 `<iframe sandbox="allow-same-origin allow-scripts?">` + CSP，隔离执行。
4. **预览**：R8-A1 编辑器调用本引擎实时预览；简历编辑页模板切换即时预览（可选）。
5. **导出**：复用现有导出图片（html2canvas）/ PDF 链路，令其基于统一引擎输出的 DOM 结构，保证「所见即所得」。
6. **数据绑定安全**：只读绑定；公开上下文（分享 / 社区）按 R1 脱敏隐藏联系方式（`phone/email/website/avatar`）。

## 3. 数据模型与配置

无新增业务表；新增 yml 配置（可开关，沿袭可选依赖风格）：

```yaml
resume:
  template:
    render:
      sandbox: true          # Schema 模板是否强制 iframe 沙箱渲染
      csp: "default-src 'self'"
      schema:
        max-depth: 4         # 布局嵌套深度上限
        max-blocks: 200      # 区块数量上限（防膨胀）
```

**校验器**：`SchemaValidator` 组件（后端）在 `template.schema` 落库前执行白名单校验（复用 R8-A1 接口）；前端 `SchemaRenderer` 二次兜底（防御非法存量数据）。

## 4. 接口

渲染引擎本身为「组件 / 服务」而非独立 REST 资源；复用既有接口：

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/api/templates/{code}` | 模板详情（返回 schema，供渲染） | 登录可选 |
| POST | `/api/templates/{id}/preview` | 沙箱渲染预览（R8-A1） | 登录（作者） |
| GET | `/api/resumes/{id}/render?template=` | 指定模板渲染只读简历 | 登录/公开（脱敏） |
| GET | `/api/resumes/{id}/export?template=&format=image|pdf` | 统一导出入口 | 登录 |

## 5. 前端改动

- 新增 `SchemaRenderer` 组件：输入 `schema` + `ResumeData`，输出统一渲染树（与导出 DOM 对齐）。
- `TemplatePreview` / 编辑器模板选择统一走 `SchemaRenderer`（`resume.ts` 模板渲染入口收口）。
- 加 `SchemaValidator`（前端）与沙箱 iframe 封装 `SandboxFrame`。
- 导出图片 / PDF 组合同一路径，减少「预览一套、导出另一套」的样式漂移。

## 6. 验收标准

- 官方三套与用户自定义模板经同一渲染引擎输出，模板切换 / 预览 / 导出样式一致。
- 恶意 schema（脚本 / 外链 / `javascript:` / 越界 `bind` / 危险 CSS）被后端拒绝且前端不加权执行；沙箱内不逃逸。
- 公开分享 / 社区只读渲染默认脱敏联系方式，与 R1 一致。
- 大 schema（节点 / 深度超限）被拒绝或降级，不导致渲染阻塞。

## 7. 难点与风险

- **XSS 与样式注入**：数据驱动 UI 的存储型 XSS 是首要风险，需「后端白名单校验 + 前端沙箱 + CSP」三道防线叠加。
- **渲染性能**：schema 节点较多时渲染 / 导出性能下降，需限制深度与区块数、虚拟化长列表。
- **官方三套迁移兼容**：现有硬编码组件与 Schema 渲染并存期需保证行为一致；迁移需逐套灰度。
- **导出一致性**：html2canvas 对复杂 CSS（网格 / 阴影 / 渐变）支持有限，需约束 schema 样式能力边界。
- **渲染引擎抽象粒度**：过粗导致扩展困难、过细导致过度设计；以「组件类型 + 数据路径」最小契约起步。