# R8-D 分享页 SEO / 社交卡片（OpenGraph + SSR 快照）

> 来源：`requirements.md` §4（R8 候选 D）
> 阶段：阶段四 | 难度：⭐⭐⭐ 中 | 依赖：R1 定点分享、R3 社区（公开详情页）、R8-A2 渲染引擎（可复用）

## 1. 定位

让公开分享的简历页（`/s/:key`）与社区详情页在**社交平台 / 搜索引擎**被爬取时能渲染出结构化元信息（标题、摘要、封面图），提升分享传播的转化与 SEO。核心是「首屏 SSR / 预渲染快照 + OpenGraph 元标签 + 社交卡片图」。

## 2. 功能点

1. **OpenGraph / meta 标签**：公开页输出 `og:title / og:description / og:image / og:type / og:url` 与 `twitter:card`，标题取简历姓名+职位，描述取摘要，图片取封面 / 模板截图。
2. **社交卡片快照图**：为分享页生成一张「卡片图」（头像 + 姓名 + 职位 + 关键亮点），作为 `og:image`；可复用 R8-A2 渲染引擎导出 DOM → 图片（html2canvas / 无头浏览器）。
3. **服务端渲染 / 预渲染**：对公开分享页做 SEO 快照（SSR 或预渲染 HTML），爬虫（无 JS 环境）可读到元信息与正文骨架；Clou+d 前后端分离的 SPA 需要「动态渲染 / prerender」中间层。
4. **落地页解析**：短链 `/s/:key` 返回的首屏（title/description/image/body）先落地，减少 JS 依赖。
5. **隐私默认脱敏**：公开页沿用 R1 脱敏（联系方隐藏 / 可选展示），meta 不泄露联系方式。

## 3. 数据模型与配置

无新增业务表；可新增 yml 配置：

```yaml
resume:
  share:
    seo:
      enabled: false              # SEO 快照开关（可降级为纯 SPA）
      prerender: false            # 是否启用动态渲染/预渲染
      og-image-default: ""        # 兜底 og:image
```

`resume_share` 可扩展封面字段（`cover_url`，R3 已用），或复用模板 `thumbnail`。

## 4. 接口（公开）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/public/og/{shareKey}` | 返回该分享的 og 元数据（title/desc/image），供 meta 注入 / 爬虫抓取 |

- 已有 `GET /api/public/shares/{key}` 返回脱敏简历；SEO 元数据接口与其对齐，匿名可访。

## 5. 前端改动

- 分享页 `/s/:key` 与社区详情页注入 `<meta property="og:*">`（`react-helmet` 或动态 head）。
- 卡片图生成：复用 R8-A2 导出图片链路，按模板渲染「分享卡片」。
- 若启用 SSR：接入 `vite-plugin-ssr` / 预渲染脚本 / 无头浏览器快照（`resume.share.seo.prerender=true` 时）。

## 6. 验收标准

- 公开分享页返回完整 `og:*` 标签，标题/描述/图片取自简历内容且默认脱敏。
- 无 JS 环境下爬虫能读到核心元信息与正文骨架（启用 prerender 时）。
- 关闭 `seo.enabled` 时不影响现有分享功能（纯 SPA 降级）。

## 7. 难点与风险

- **SSR 引入的运维复杂度**：前后端分离 SPA 无 SSR，需评估用「预渲染静态快照」而非引入完整 SSR 框架，避免架构大改。
- **抓取一致性**：og 快照与实时简历内容可能滞后，需在分享创建 / 更新时刷新快照（事件驱动 / 失效标记）。
- **图片生成成本**：卡片图渲染（无头浏览器）开销与稳定性，可缓存 + 按需生成。
- **隐私**：meta 与卡片图零泄露联系方式，公开页脱敏与 R1 一致。
- **收益有限**：简历分享页 SEO 价值一般，建议按需启动，不作为高优先项。