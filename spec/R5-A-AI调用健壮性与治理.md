# R5-A AI 调用健壮性与治理（R5/R6 共用）

> 来源：`R5-面试题目生成.md` §2「治理」与 §9「难点」、`R6-AI修改完善简历.md` §9 的扩展
> 阶段：阶段三 | 难度：⭐⭐⭐⭐ 中高 | 依赖：AI 能力层（R5/R6/R8 共用）、可选 Redis、消息队列（R10-O3）

## 1. 定位

盘点 R5（面试题生成）与 R6（简历 AI 润色 / 扩写 / 建议）在五个维度的实现现状与缺口，并给出落地规划：

1. 前端接线是否完成
2. 后端如何避免死循环
3. 发给 LLM 的输入是什么、如何处理
4. 是否用消息队列削峰
5. 同一用户输入是否有频控

本文承接 R5 spec §2 的「AI 能力层治理：请求限流 / token 用量记录 / 超时熔断 / 输入输出内容安全」，是 R5/R6 共用的健壮性与治理补充（R5 承载，R6 复用）。

## 2. 结论速览

| 维度 | 现状 | 结论 |
|------|------|------|
| 前端接线 | 已完成 | ✅ 无需再改（`npm run build` 通过） |
| 防死循环 | 循环上界 + 兜底 + 单次调用超时 + 熔断短路 | ✅ 已完成（见 §7.1） |
| LLM 输入处理 | 已做脱敏 + 截断 + ReAct 上下文 | ✅ 已完成 |
| 消息队列削峰 | 已落地「内存双队列」异步（opt-in，`ai.async.enabled`） | ✅ 已实现（Redis Stream / Kafka 同接口后续） |
| 单用户频控 | 按 `user_id + task_type` 计数（Redis / 本地内存可切换），超限 429 | ✅ 已实现（见 §7.3） |

## 3. 前端现状（已实现）

R5/R6 用户端前端接线在早前会话完成，后台后端 ReAct 改造并未影响前端：

- `src/pages/InterviewPage.tsx` + `App.tsx` `/interview` 路由：面试题生成页。
- `src/components/AiTextActions.tsx`：内嵌于 `PersonalEditor / ExperienceEditor / ProjectsEditor` 三个编辑器的「润色 / 扩写」按钮。
- `src/components/AiSuggestModal.tsx` + `Toolbar.tsx`「AI 建议」入口：整份简历的诊断建议。
- `src/pages/ResumeListPage.tsx`：「面试准备」入口。

无需新增前端工作；如需「异步任务进度（SSE / 轮询）」则要新增（见 §7.2）。

## 4. 防死循环（现状 + 缺口）

后端 ReAct 引擎已内置多道防死循环手段（[ReActEngine.java](../../resume-server/src/main/java/com/resumegen/ai/ReActEngine.java)）：

1. **循环上界**：`for (int i = 0; i < max; i++)`，`max = Math.max(1, ai.react.max-iterations)`，默认 4 轮，`Math.max(1, …)` 防止配置成 0 导致不执行。
2. **协议兜底**：每轮输出必须是 `{"action":"KB_SEARCH"}` 或 `{"final_answer":…}`；解析失败追加提示继续、出现未知工具追加提示继续，均不进入死循环。
3. **耗尽兜底**：循环耗尽后强制单次 `generateText`（`"请忽略此前步骤，直接输出最终答案"`），再解析失败则 `requireNode` 抛 `BusinessException(500)` 结束。

单次调用序列上界：**预规划 1 次 + ReAct 最多 4 轮 + 兜底 1 次 = 最多 6 次 LLM 调用**。

**缺口：无超时 / 熔断。** `ChatClient.call().content()`（在 [AiGateway.java](../../resume-server/src/main/java/com/resumegen/ai/AiGateway.java) `raw()`）为同步阻塞，未加调用超时；若上游挂起，单个请求（含最多 6 次调用）会长期占用线程，并发下可能拖垮服务。规划见 §7.1。

## 5. 发给 LLM 的输入与处理（已实现）

### 5.1 输入构造

- **模板**：`Skills.*_PROMPT` 内置默认模板（`interview.gen_questions` / `resume.rewrite` / `resume.expand_star` / `resume.suggest`），占位符 `{{context}} / {{section}} / {{text}} / {{resume}} / {{targetRole}} / {{jd}}`。
- **填充**：`AiSupport.fill()` 做 `{{var}}` 替换；`knowledge.skillPrompt()` 优先从 `ai_kb_entry(skill_prompt)` 热取模板，查不到回退内置默认。
- **系统提示**：`Skills.SYSTEM_JSON_RULE`（只出 JSON）+ ReAct 阶段的 `SYSTEM_PLANNER` / `SYSTEM_REACT`（见 [Skills.java](../../resume-server/src/main/java/com/resumegen/ai/Skills.java)）。

### 5.2 输入处理（安全 / 成本）

1. **个人信息脱敏**：`AiSupport.desensitizedResumeJson()` 序列化前剥离 `phone / email / website / avatar`，再发给 LLM。
2. **长度截断**：`ai.max-context-chars`（默认 8000）截断简历 / 上下文，防超长导致 token 爆表。
3. **RAG 上下文**：不再在调用前预取拼入（`{{context}}` 现填空），改为 ReAct 循环内按需用 `KB_SEARCH` 检索（`AiKnowledgeService.search()` = 向量相似检索 → 无命中回退直读 few-shot），检索结果以「观察」注入下一步 prompt。
4. **结构化收敛**：最终答案以 `final_answer` JSON 返回，`ReActEngine.executeObject/executeList` 反序列化到目标 VO；解析失败抛 `BusinessException(500)`。

### 5.3 token 用量记录（已实现）

每次生成的输入 / 输出经 `AiGenerationLog`（`ai_generation_log`）落库，`AiSupport.estimateTokens()` 粗估 token、`AiSupport.sha256()` 记输入指纹、`status` 记成功失败，作为成本与审计依据。

## 6. 现状代码索引

| 组件 | 文件 |
|------|------|
| ReAct 引擎 | `resume-server/.../ai/ReActEngine.java` |
| 网关（同步调用 + 降级） | `.../ai/AiGateway.java` |
| 输入处理（脱敏 / 截断 / 填充 / sha256） | `.../ai/AiSupport.java` |
| 知识检索（RAG + KB_SEARCH 工具） | `.../ai/AiKnowledgeService.java` |
| 协议 / 模板 prompt | `.../ai/Skills.java` |
| R5 服务 | `.../service/InterviewService.java` |
| R6 服务 | `.../service/ResumeAiService.java` |
| 配置 | `.../config/AiProperties.java` + `application.yml`（`ai.react.*`） |

## 7. 缺口与落地规划

### 7.1 超时 + 熔断（防死循环收尾）—— ✅ 已落地

- **已落地**：`AiGateway.raw()` 通过守护线程池异步执行阻塞的 `ChatClient.call()` 并用 `future.get(ai.timeout-seconds)` 限时，超时 / 中断 / 异常均释放主调用线程并映射为 `SERVER_ERROR(500)`；新增 `AiCircuitBreaker`（CLOSED → OPEN → HALF_OPEN 状态机），连续失败达 `failure-threshold` 后短路 `open-seconds` 秒直接返回「AI 服务繁忙，请稍后重试」，试探成功自动恢复。均可通过 `ai.circuit-breaker.*` 与 `ai.timeout-seconds` 配置。
- **目标**：单次 LLM HTTP 调用设超时（`ai.timeout-seconds`，默认 60s），超时即失败并释放线程；连续失败触发熔断（短路若干秒直接返回降级错误），避免雪崩。
- **方案**：Spring AI 底层走 `spring.ai.openai.*` 的 `RestClient` / `WebClient` 连接池，可通过 `spring.ai.openai.chat.options` 或自定义 `RestClientCustomizer` 注入超时；熔断可引入 Resilience4j `@CircuitBreaker` 包裹 `AiGateway.raw()`（依赖可选、可开关）。
- **兜底语义**：超时 / 熔断均映射为 `ErrorCode.SERVER_ERROR(500)`，前端提示「AI 服务繁忙，请稍后重试」。

### 7.2 消息队列削峰（异步化生成，按成本双队列）—— ✅ 已落地（内存实现）

- **已落地**：`MessageQueue` 抽象 + 默认 `InMemoryMessageQueue`（每队列独立消费线程池）；`AiAsyncService` 按成本分 `high`/`low` 双队列提交，新增 `/api/ai/**/async` 提交端点（返回 `taskId`）与 `GET /api/ai/task/{taskId}` 轮询端点。`ai.async.enabled=false`（默认）时同步链路不受影响。Redis Stream / Kafka 为同接口后续实现。
- **已落地（SSE 完成推送）**：任务完成后由 `AiAsyncService` 触发监听器推送 —— 新增 `GET /api/ai/task/{taskId}/stream`（`SseEmitter`）将 SUCCESS/FAILED 结果一次推给前端；`AuthInterceptor` 支持 `?token=` 查询参数以便 `EventSource`（无法自定义请求头）鉴权；前端 `streamTaskResult()` 用 `EventSource` 订阅，`VITE_AI_ASYNC_ENABLED=true` 时面试题库生成走异步+SSE，否则回退同步链路。
- **现状（未开启异步时）**：R5/R6 仍是同步阻塞链路（Controller → Service → ReActEngine → `ChatClient.call()`）；默认 `ai.async.enabled=false`，需显式开启并让前端切到 async 端点。
- **方案（复用 R10-O3 `MessageQueue` 抽象）**：
  1. 抽象 `MessageQueue`（先 Redis Stream 落地，兼容 RabbitMQ / Kafka），`resume.cache.type=none` 时提供「内存队列 + 本地线程池」降级实现或不开启异步。
  2. 生成类接口（`interview/generate`、`resume/rewrite|expand|suggest`）改为**提交任务返回 `taskId`**，消费者按队列并发上限拉取消费，天然控并发削峰。
  3. **按成本分双队列、差异化资源**：高成本任务与低成本任务分属独立队列，各自配置不同消费并发——高成本队列并发槽位更多（吞吐优先），低成本队列并发更少（省上游额度 / 线程），避免轻任务挤占重任务资源，实现资源合理分配。
  4. 结果落地（复用 `interview_set` / `ai_generation_log`），前端通过 **SSE 或轮询** 获取进度与结果。

- **任务分级（建议默认，可配置调整）**：

| 队列 | 承载任务 | 成本特征 | 消费并发（示例） |
|------|----------|----------|------------------|
| `high` | `interview/generate`、`resume/suggest` | ReAct 多轮 / 全文输入 / 输出量大 | 高（如 8） |
| `low` | `resume/rewrite`、`resume/expand` | 单段短输入 / 单轮直出 | 低（如 2~3） |

- **决策点**：异步会改变前端交互（生成从「等同步返回」变「轮询进度」），需评估「重生成 / 删除 / 建议」等轻量操作是否也走队列，或仅对高成本「题库生成」异步。

### 7.3 单用户频控（限流）—— ✅ 已落地

- **已落地**：新增 `RateLimitStore` 抽象（`InMemoryRateLimitStore` 本地滑动窗口 / `RedisRateLimitStore` INCR+EXPIRE，由 `resume.cache.type` 选择）与 `AiRateLimiter` 门面，在 `InterviewController` / `ResumeAiController` 的同步与 async 端点统一 `rateLimiter.check(userId, taskType)`。超限抛 `RATE_LIMITED(429, "请求过于频繁，请稍后重试")`。
- **方案**：`ai.rate-limit` 配置段（**可配置、可关闭**），按「`user_id` + `task_type`」维度计数：
  - `ai.rate-limit.enabled`：开关，默认 `false`（关闭不限流）。
  - `ai.rate-limit.limit` / `window-seconds`：固定窗口，`per-type` 按任务类型覆盖（如 `interview.generate: 3`，其余走 `limit: 20`）。
  - 计数存储：`cache.type=redis` 用 Redis 原子计数（`INCR` + `EXPIRE`）；`cache.type=none` 用本地内存滑动窗口（单机近似，多实例不严格）。
- **错误码**：新增 `ErrorCode.RATE_LIMITED(429, "请求过于频繁，请稍后重试")`，沿用统一响应体 `{code,message,data}`（body 内 `code=429`）。

### 7.4 输入输出内容安全（可选）

- **输入**：已做个人信息脱敏（§5.2）；如需可增加敏感词 / 违规内容前置拦截（复用社区 `community.sensitive-words` 思路）。
- **输出**：提示注入 / 越权指令防护目前依赖 prompt 约束（`SYSTEM_JSON_RULE` + `final_answer` 必须 JSON），可规划输出 JSON 强校验 + 违规关键词过滤，防止模型输出被用于 prompt 注入到前端。

## 8. 配置示例（已落地）

```yaml
ai:
  enabled: ${AI_ENABLED:false}
  max-context-chars: 8000
  timeout-seconds: 60                 # 单次 LLM 调用超时（7.1）
  circuit-breaker:                    # 熔断：连续失败短路（7.1）
    enabled: true
    failure-threshold: 5              # 连续失败次数阈值
    open-seconds: 30                  # 熔断打开后短路时长（秒）
  rate-limit:                         # 单用户频控（7.3）
    enabled: ${AI_RATE_LIMIT_ENABLED:false}
    window-seconds: 60
    limit: 20                         # 默认每窗口上限
    per-type:                         # 按任务类型覆盖（高成本额度更低）
      interview.generate: 3
  react:
    enabled: ${AI_REACT_ENABLED:true}
    max-iterations: 4
  async:                              # 消息队列削峰，按成本双队列（7.2）
    enabled: false
    high:
      concurrency: 8                  # 高成本队列（interview/generate、resume/suggest），资源多
    low:
      concurrency: 3                  # 低成本队列（resume/rewrite、resume/expand），资源少
```

## 9. 验收标准

- 前端五入口可用，构建通过（已满足）。
- ReAct 任意异常输出 / 未知工具 / 循环耗尽都能在有限次调用内结束（≤6 次），不出现死循环（已满足）。
- 发送 LLM 的文本不包含 `phone/email/website/avatar` 等敏感字段，且不超过 `max-context-chars`（已满足）。
- 调用超时可中断并返回明确错误（已满足，`AiGateway` 超时线程 + `500` 提示）。
- 高频调用下由队列削峰、worker 并发有上限，服务不被打满（已满足，`ai.async.enabled=true` 时生效）。
- 同一用户超过 `rate-limit` 阈值时返回 429（已满足，`ai.rate-limit.enabled=true` 时生效）。

## 10. 难点与风险

- 同步改异步会改动前端交互，故采用「新增独立 `/async` 端点 + `taskId` 轮询」而非替换原同步接口，避免破坏现有同步链路。
- 当前异步结果暂存于内存 `AiAsyncService` 登记表，**进程重启即丢失**；持久化 / 断点恢复需后续接入 `ai_task` 表或 Redis（`MessageQueue` 接口不感知）。
- 本地内存限流 / 队列在多实例部署下不严格（需 Redis 才能真正全局），需明示降级边界。
- 熔断 / 超时采用「守护线程池 + `Future.get(timeout)`」在调用线程侧限时：超时释放的是调用线程，**已发出的底层 HTTP 调用线程（守护）可能仍短暂占用 socket**，极端并发下的线程占用需结合 `ai.async` 队列并发上限兜底。
- 超时 / 熔断阈值若过紧会误伤正常生成，需与模型实际响应时长（ReAct 多轮）做平衡。
- Redis 限流用「INCR 后条件 EXPIRE」近似固定窗口，窗口边界在极端并发下可能有少量偏差；如需严格精确可换 Lua 令牌桶。