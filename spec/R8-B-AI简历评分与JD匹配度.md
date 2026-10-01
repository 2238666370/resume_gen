# R8-B AI 简历评分 + JD 匹配度（ATS 打分 + 可解释建议）

> 来源：`requirements.md` §4（R8 候选 B）
> 阶段：阶段四 | 难度：⭐⭐⭐⭐ 中高 | 依赖：AI 能力层（Spring AI，R5/R6 共用）、R5 面试能力分层可复用

## 1. 定位

把「简历好坏」从主观判断变成可量化的 **ATS 风格评分 + JD 匹配度**。输入某份简历（可选一份岗位 JD），输出总分、分维度得分、缺失/薄弱项与可解释的改进建议。既是增值付费点，也为 R5（面试）、R6（改写）提供「先评后改」的入口。

## 2. 功能点

1. **简历评分**（无 JD）：按完整性、量化度、动词质量、结构清晰度、长度、格式等维度打分（0–100），给出雷达/条形分布。
2. **JD 匹配度**（有 JD）：解析 JD 中的硬技能 / 软技能 / 经验年限 / 学历要求，与简历逐项匹配，输出匹配百分比 + 命中的关键词高亮 + 缺失项清单。
3. **可解释建议**：每个低分维度 / 缺失项都附带 1~2 条可执行建议（可触发 R6 `rewrite/expand/suggest` 直接改）。
4. **结果复用**：评分结果与 R6 建议打通，支持「基于评分一键优化 / 定向补写缺失技能」。
5. **持久化**：评分结果落 `ai_generation_log`（task_type=`resume.score`），历史可查询（复用 AI 历史查询）。

## 3. 数据模型

复用 `ai_generation_log`（task_type 扩展 `resume.score`），无新增业务表；评分明细（维度分数 + 建议）存 `output` JSON：

```json
{
  "totalScore": 82,
  "matchedPercent": 68,
  "dimensions": [
    { "key": "completeness", "name": "完整性", "score": 85, "comment": "缺少项目量化结果" }
  ],
  "skillHits": ["java", "spring boot", "mysql"],
  "missingSkills": ["redis", "kafka"],
  "suggestions": ["在项目经历中补充 STAR 量化结果", "工作经历增加半年以上在职时长说明"]
}
```

## 4. 接口（`/api/ai/resume/*`，登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/resume/score` | 简历评分（可选 `jd`） |
| POST | `/api/ai/resume/score/async` | 评分异步提交（返回 `taskId`） |
| GET | `/api/ai/history?taskType=resume.score` | 历史评分记录（复用历史查询） |

- 输入走 R6 同款脱敏 + 截断（`AiSupport.desensitizedResumeJson`）。
- 接入单用户频控（`resume.score` 项）、超时熔断、异步双队列（高成本队列）。

## 5. 前端改动

- 编辑器 `Toolbar` 新增「AI 评分」入口 → `AiScoreModal`：展示总分、维度雷达图 / 条形、JD 命中关键词高亮、缺失清单、建议（可一键跳到对应模块编辑）。
- `api/ai.ts` 新增 `aiScore` / `aiScoreAsync`；复用 `streamTaskResult` 与 `ProgressBar` 进度提示。

## 6. 验收标准

- 有 JD 时输出匹配百分比 + 命中技能 + 缺失技能；无 JD 时输出可解释的维度评分与建议。
- 评分结果可追溯（`ai_generation_log`），历史可查；限流 / 超时 / 密钥缺失返回明确错误。
- 建议可一键联动 R6 改写（字段级 diff + 乐观锁回写），不破坏其他数据。

## 7. 难点与风险

- **可解释性**：模型打分主观且不可复现，需用「维度 + 量化规则 + 建议」约束输出结构，避免黑盒裸分。
- **结构化输出稳定性**：复用 R5 `SYSTEM_JSON_RULE` + 重试 + 容错，保证 `dimensions/missingSkills/suggestions` 字段合规。
- **评分一致性**：同一简历多次评分波动大，可加「规则先算 + LLM 解释」混合策略，规则分保证稳定基线。
- **成本**：全文 + JD 输入较长，纳入高成本队列与限流。
- **合规**：评分涉及个人信息与岗位地域差异，需提示「仅供参考，不应作为唯一录用依据」。