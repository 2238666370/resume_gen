# R6 AI 修改 / 完善简历（Spring AI + RAG + Skill，默认 DeepSeek）

> 来源：`requirements.md` §4
> 阶段：阶段三 | 难度：⭐⭐⭐⭐⭐ 高 | 依赖：AI 能力层

## 1. 定位

AI 润色、扩写、补全经历描述与自评，或给整体优化建议，用户逐条采纳合并回简历。技术栈选用 **Spring AI**：通过 **RAG** 引入优秀简历样例 / 写作范式提升表达质量，通过 **Skill** 将「润色 / 扩写 / 建议 / 差异合并」拆为可复用工具，模型按意图自动编排。

## 2. 前置：AI 能力层基建

复用 R5「AI 能力层」（`ChatClient` + `EmbeddingModel` + `VectorStore` + Skill），详见 R5 spec；健壮性与治理（防死循环 / 脱敏截断 / 队列削峰 / 频控）见 `R5-A-AI调用健壮性与治理.md`。

## 3. 功能点

1. **润色 rewrite**：原文 → 更正式表述，采纳替换。
2. **扩写 expand**：要点 → STAR 结构化描述（RAG 命中动词库 / 量化范式作 few-shot）。
3. **建议 suggest**：全文分析 → 改进建议列表（不改稿）。
4. **采纳合并 apply**：某条「应用」→ 输出字段级 diff → 乐观锁回写（复用 `PUT`/`PATCH` 语义）。

## 4. RAG 与 Skill 设计（R6 增量）

### 4.1 RAG 知识库（`ai_kb_entry`，按 `kb_type` 区分）

| kb_type | 内容 | 用途 |
|---------|------|------|
| `resume_sample` | 优秀经历描述 / 自评样例（按行业 / 岗位 / 模块分类） | 润色 / 扩写 few-shot |
| `writing_style` | 动词库、量化成果句式、STAR 模板等写作范式 | 提升表达质量 |

### 4.2 Skill 编排（Spring AI 函数化，`@Tool`）

| Skill | 职责 | 输出 |
|-------|------|------|
| `resume.rewrite` | 单段润色（更正式 / 更量化） | `{original, revised}` |
| `resume.expand_star` | 要点 → STAR 结构化描述 | `{original, expanded}` |
| `resume.suggest` | 全文诊断，输出改进建议列表 | `[{section, issue, advice, priority}]` |
| `resume.apply_diff` | 将采纳项转字段级 diff（不破坏其它字段） | `{patch fields}` |

## 5. 数据模型

复用 `ai_kb_entry`（RAG 知识库）与 `ai_generation_log`：

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

## 6. 接口（`/api/ai/resume/*`，需登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/resume/rewrite` | 润色 |
| POST | `/api/ai/resume/expand` | 扩写（STAR） |
| POST | `/api/ai/resume/suggest` | 全文建议 |
| POST | `/api/ai/resume/apply` | 采纳合并（乐观锁保存） |

## 7. 前端改动

各模块编辑器（`ExperienceEditor` 等）加 AI 按钮；建议列表逐条采纳 / 忽略；采纳后乐观锁保存，冲突提示（409）。

## 8. 验收标准

- 润色 / 扩写返回可用文本，采纳正确回写且不破坏其他数据。
- 建议模式不改稿；采纳走乐观锁，冲突有提示；生成记录可追溯。
- RAG few-shot 提升表达质量（可 A/B 对比），`rag.enabled=false` 降级后仍可用。

## 9. 难点与风险

- **结构化输出对齐 `ResumeDTO`**：`BeanOutputConverter` + JSON Schema 约束；`apply_diff` 仅含目标字段，防误删。
- **防 AI 虚构经历**：prompt 只润色不编造 + 输出校验（不得新增不存在的公司 / 项目 / 技能）。
- **diff / apply 防误删字段** + 乐观锁冲突处理（409）。
- 配额计费与成本控制。