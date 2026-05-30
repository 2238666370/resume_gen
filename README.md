# 简历生成器 Resume Generator

一个基于 React + TypeScript 的在线简历制作工具，支持多套模板切换、可拖拽模块排序、富文本加粗、导出 PNG/PDF。

---

## 快速启动

### 方式一：桌面应用（推荐）

直接双击 `release/win-unpacked/简历生成器.exe` 即可运行，无需安装 Node.js。

### 方式二：双击 bat（浏览器）

1. 确保已安装 **Node.js**（≥18.x，推荐 22.x）
2. 双击 `start.bat` → 自动打开浏览器

### 方式三：开发模式

```bash
# 1. 进入项目目录
cd resume_gen

# 2. 安装依赖
npm install

# 3. 启动开发服务器
npm run dev
```

启动成功后访问：**http://localhost:5173**

### 打包桌面应用

```bash
npm run electron:build    # 构建 dist + 打包为 .exe（产物在 release/）
npm run electron:dev      # Electron 开发模式（热重载需手动刷新）
```

### 其他命令

```bash
npm run build    # 生产构建，产物输出到 dist/
npm run preview  # 本地预览生产构建
npm run lint     # ESLint 代码检查
```

---

## 功能说明

### 模板选择

点击顶部工具栏的模板图标可在三套模板间切换：

| 模板 | 风格 |
|------|------|
| 经典（Classic） | 上方信息头 + 下方各模块，专业正式 |
| 现代（Modern） | 左侧彩色侧边栏 + 右侧正文，个性时尚 |
| 简约（Minimal） | 极简双栏排版，清爽干净 |

### 模块管理

左侧面板列出所有简历模块，支持：

- **展开/折叠**：点击模块标题展开填写表单
- **显示/隐藏**：右侧 toggle 开关控制是否渲染到简历
- **拖拽排序**：拖动左侧 ⠿ 图标改变模块顺序（仅影响预览，不影响编辑面板顺序）
- **添加自定义板块**：底部「+ 添加自定义板块」可创建任意命名的文本板块

### 支持的模块

| 模块 | 说明 |
|------|------|
| 个人信息 | 姓名、联系方式、求职意向、头像等 |
| 工作经历 | 公司名、职位、时间段、工作内容（多条） |
| 实习经历 | 同工作经历结构，独立板块 |
| 教育经历 | 学校、专业、学历、时间段（多条） |
| 专业技能 | 技能名 + 熟练度滑块（多条） |
| 项目经历 | 项目名、角色、时间段、描述（多条） |
| 证书荣誉 | 名称 + 时间（多条） |
| 语言能力 | 语言名 + 等级（多条） |
| 个人简介 | 自由文本 |
| 自定义板块 | 可改名的自由文本板块，可添加多个 |

### 文本加粗

所有多行文本字段均支持 Markdown 风格的加粗语法：

```
用 **文字** 包裹需要加粗的内容
```

示例：
```
• 负责 **前端架构** 设计与落地
• 优化页面加载速度 **35%**，日活提升 **2万**
```

输入后实时渲染为加粗效果，导出时完全保留。

### 配色主题

顶部工具栏右侧提供：
- 8 种预设强调色
- 任意取色器（拾取自定义颜色）

颜色会同步应用到三套模板的标题、线条、侧边栏等强调元素。

### 导出

| 格式 | 说明 |
|------|------|
| 导出图片（PNG） | 高清 2× 分辨率，适合截图分享 |
| 导出 PDF | 文字型 PDF，中文可复制、可搜索，页边距为零 |

> **PDF 导出机制**：
> - **桌面应用**：通过 Electron 主进程 `printToPDF` 直接生成，弹出保存对话框，无需额外操作
> - **浏览器**：弹出打印对话框，选择「另存为 PDF」即可保存。由于使用浏览器原生渲染引擎，所有系统字体（包括中文 PingFang SC / Microsoft YaHei 等）均可完美呈现，生成的 PDF 支持文字搜索和复制粘贴。
>
> 点击顶部工具栏对应按钮即可，导出过程约 1–3 秒。

### 保存与导入

| 操作 | 说明 |
|------|------|
| **保存** | 将当前简历数据导出为 `.json` 文件下载到本地 |
| **导入** | 选择一个之前保存的 `.json` 文件，恢复全部简历数据和设置 |
| 自动保存 | 数据同时通过 `localStorage` 自动持久化，刷新不丢 |

> 建议在填写完整简历后点「保存」导出一份 JSON 备份，可以随时通过「导入」恢复。

### 数据持久化

简历数据通过 **zustand persist** 自动保存到浏览器 `localStorage`，刷新页面后数据不丢失。顶部工具栏提供「重置」按钮，点击后二次确认可清空所有内容。

---

## 项目架构

```
resume_gen/
├── src/
│   ├── types/
│   │   ├── resume.ts          # 全局 TypeScript 类型定义
│   │   └── electron.d.ts      # window.electron IPC 桥接类型声明
│   ├── store/
│   │   └── resumeStore.ts     # Zustand 全局状态 + CRUD 操作
│   ├── components/
│   │   ├── Toolbar.tsx        # 顶部工具栏（模板切换/配色/导出/重置）
│   │   ├── editor/            # 左侧编辑面板各模块
│   │   │   ├── SectionsPanel.tsx        # 模块列表容器（拖拽容器）
│   │   │   ├── PersonalEditor.tsx       # 个人信息编辑
│   │   │   ├── ExperienceEditor.tsx     # 工作经历编辑
│   │   │   ├── InternshipEditor.tsx     # 实习经历编辑
│   │   │   ├── EducationEditor.tsx      # 教育经历编辑
│   │   │   ├── SkillsEditor.tsx         # 专业技能编辑
│   │   │   ├── ProjectsEditor.tsx       # 项目经历编辑
│   │   │   ├── OtherEditors.tsx         # 证书/语言/个人简介编辑
│   │   │   ├── CustomSectionEditor.tsx  # 自定义板块编辑
│   │   │   └── BoldHint.tsx             # 加粗语法提示组件
│   │   └── templates/         # 三套简历模板
│   │       ├── ClassicTemplate.tsx
│   │       ├── ModernTemplate.tsx
│   │       └── MinimalTemplate.tsx
│   ├── utils/
│   │   ├── export.ts          # 导出 PNG/PDF/JSON + 导入 JSON 解析
│   │   └── textRenderer.tsx   # **加粗** 语法解析 → React 节点
│   ├── App.tsx                # 根组件，整体三栏布局
│   ├── main.tsx               # React 入口
│   └── index.css              # Tailwind 基础样式引入
├── main.cjs                   # Electron 主进程（窗口 + IPC handler）
├── preload.cjs                # Electron preload（安全 IPC 桥接）
├── tailwind.config.js         # Tailwind v3 配置
├── postcss.config.js          # PostCSS 配置
├── vite.config.ts             # Vite 5 构建配置
└── package.json
```

### 技术选型

| 技术 | 版本 | 用途 |
|------|------|------|
| React | 19 | UI 框架 |
| TypeScript | 6 | 类型安全 |
| Vite | 5 | 构建工具（兼容 Node 22） |
| Tailwind CSS | 3 | 原子化样式 |
| Zustand | 5 | 轻量全局状态管理（含 persist） |
| @dnd-kit | 6/10 | 无障碍可访问的拖拽排序 |
| html2canvas | 1.4 | DOM → Canvas 截图（PNG 导出） |
| Electron | 34 | 桌面应用框架（exe 打包） |
| electron-builder | 26 | Electron 打包工具 |
| 浏览器原生打印 | - | 浏览器环境文字型 PDF 导出 |

### 数据流

```
用户操作编辑器
      ↓
  Zustand Store（resumeStore）
      ↓ 自动持久化到 localStorage
      ↓
  三套模板组件读取 store 数据实时渲染
      ↓
  导出 PNG：html2canvas 截图 → 下载
  导出 PDF（Electron）：收集 CSS + DOM → IPC → 主进程 printToPDF → 保存对话框
  导出 PDF（浏览器）：DOM 克隆到 iframe → 浏览器打印 → 另存为 PDF
```

### 状态结构（ResumeData）

```typescript
{
  personal: PersonalInfo,        // 个人信息
  sections: Section[],           // 所有模块的排序与显示状态
  experiences: Experience[],     // 工作经历列表
  internships: Experience[],     // 实习经历列表
  educations: Education[],       // 教育经历列表
  skills: Skill[],               // 技能列表
  projects: Project[],           // 项目列表
  certificates: Certificate[],   // 证书列表
  languages: Language[],         // 语言能力列表
  summary: string,               // 个人简介
  customSections: CustomSection[], // 自定义板块列表
  template: 'classic' | 'modern' | 'minimal',
  primaryColor: string           // 主题色（hex）
}
```

---

## 常见问题

**Q: 导出 PDF 后中文能搜索和复制吗？**  
A: 可以。PDF 通过浏览器原生打印生成，文字以真实文本形式存在，支持搜索、复制粘贴，中文完美呈现。

**Q: 导出 PDF 字体发虚/模糊？**  
A: 浏览器打印输出为矢量文字，不会发虚。如画面模糊，请检查打印设置中是否误选了低质量模式。

**Q: 刷新后数据丢失？**  
A: 数据存储在 `localStorage`，清除浏览器数据会导致丢失。重要简历建议及时导出。

**Q: 图片上传头像不生效？**  
A: 当前版本头像通过 URL 填写（将图片上传到图床后粘贴链接）。

**Q: 自定义板块可以添加几个？**  
A: 无限制，但过多会影响导出 PDF 的排版，建议不超过 3 个。
