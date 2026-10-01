# R3-A 社区计数写扩散方案（点赞/收藏/评论/举报）

> 来源：`R3-简历社区.md` 难点扩展
> 阶段：阶段二 | 难度：⭐⭐⭐⭐ 中高 | 依赖：R3 社区、R10-O3 异步、可选 Redis

## 1. 定位

将社区互动计数（点赞、收藏、评论、举报、浏览）从「读时聚合」重构为「**写扩散（写时聚合）**」：写操作发生时即完成聚合计数的增减，读操作直接从聚合值读取，无需在查询时 `COUNT(*)`。允许读 / 写短暂不一致，保证最终一致性。

**为什么用写扩散**：社区信息流是典型「读多写少」，信息流 + 详情每次都要展示计数。读时聚合（读扩散）会导致每次列表 / 详情都对明细表 `COUNT(*)` 或 join 明细，读放大严重；写扩散把成本前移到低频写路径，让高频读路径 O(1) 直读聚合值。

## 2. 设计原则

- **读写分离**：计数是「读模型」（冗余聚合值），明细表是「事实模型」（幂等、可对账的真相源）。
- **写扩散**：计数在写路径上 +1 / -1 维护，读路径零计算。
- **最终一致**：计数可短期漂移，通过「DB 写透 + 读穿透回填 + TTL 过期自愈」收敛；「是否已点赞 / 收藏」用明细唯一键保证强一致。
- **可降级**：沿袭 `cache.type = redis | none`，Redis 缺失时降级 DB 直写，功能不缺失。

## 3. 数据模型

沿用 R3 现有表，无需新增；计数列仍在 `community_post`：

| 计数 | 读模型（冗余列） | 事实模型（明细表 + 唯一键） |
|------|------------------|------------------------------|
| 点赞 | `community_post.like_count` | `community_like`（`uk_post_user`） |
| 收藏 | `community_post.collect_count` | `community_collect`（`uk_post_user`） |
| 评论 | `community_post.comment_count` | `community_comment` |
| 举报 | `community_post.report_count` | `community_report` |

> 热点拆分（可选，进阶）：热度极高时把计数列拆到独立 `community_counter(post_id, like_count, ...)` 或 Redis，避免同一行高频更新造成行锁竞争（见 §6）。

## 4. 写路径（写扩散，写时聚合）

### 4.1 主链路（`cache.type=redis`）

1. **明细落库**：写 `community_like`（唯一键幂等）——真相源，保证「是否点过」强一致。
2. **DB 写透**：`UPDATE community_post SET like_count = like_count + 1`（与明细同事务）——DB 计数始终强一致，兜底 `hot` 排序 / 高热度复审 / 管理端。
3. **Redis 读数聚合**：`HINCRBY post:counter:{postId}`（读模型，写扩散），写后 `EXPIRE` 设 TTL。
4. **返回**：返回动作结果 + 最新计数。

### 4.2 降级链路（`cache.type=none`）

同一数据库事务内：写明细表 + `UPDATE community_post SET like_count = like_count + 1`（写透）。读时直接读计数列。

### 4.3 取消操作

删除明细（唯一键）→ 计数 `-1`（`DECR` / `UPDATE ... - 1`），下限 0 防负数。

## 5. 读路径（零聚合）

- 列表 / 详情 / 互动：优先读 Redis hash 聚合值（`HGETALL`），命中空则读 `community_post` 计数列并读穿透回填；一次读，不 `COUNT(*)`。
- 列表需按热度排序时，读 `community_post` 计数列（DB 写透，与 Redis 一致），或 Redis `ZSET` 热度榜（见 §6）。
- 「我是否点赞 / 收藏过」：与计数解耦，从明细表 `EXISTS`（或批量 `IN`）查询，不影响计数读性能。
- **浏览数例外**：`view_count` 为「读取即 +1」语义（非用户写操作），保持 DB `incrView` + 详情展示 `+1`，不走 Redis 计数。

## 6. 热点与排序优化（可选）

- **热度榜**：Redis `ZSET key=post:hot`，写扩散时顺带 `ZINCRBY`，列表读 `ZREVRANGE` 得 TopN，避免全量 `ORDER BY`.
- **热点拆分**：高热度帖子单行计数更新会形成热点，拆 `community_counter` 表 / Redis 分片 key 分散锁竞争。

## 7. 最终一致性保障

1. **DB 写透**：计数列与明细同一事务提交，DB 计数始终强一致（复审 / 排序 / 管理端不滞后）。
2. **读穿透回填**：Redis 未命中时，以 DB 聚合值 `saveAll` 回填缓存，读侧自愈。
3. **TTL 过期自愈**：Redis 计数 key 设 24h TTL，过期后下次读穿透从 DB 重建，天然收敛漂移（兑付「允许短暂不一致、保证最终一致」）。
4. **失败重试**：计数 `INCR` 失败走重试 / 消息队列（R10-O3，`MessageQueue` 抽象，先 Redis Stream、后续兼容 RabbitMQ / Kafka），保证不丢。
5. **一致性边界**：计数允许秒级漂移，最终收敛；身份 / 幂等始终强一致。

## 8. 验收标准

- 写操作返回后，读接口立即读到聚合后的数值（写扩散生效，不再 `COUNT(*)`）。
- 同一用户重复点赞幂等，计数只 +1；取消只 -1 且不为负。
- Redis 抖动 / 重启 / 丢失后，读穿透 + TTL 过期从 DB 重建，计数收敛到 DB 真实值（最终一致）。
- `cache.type=none` 时降级 DB 直写，读接口仍正确返回计数。

## 9. 难点与风险

- 计数与明细短期不一致（已明确接受，最终一致兜底）。
- Redis 计数丢失 / 漂移风险（读穿透 + TTL 过期自愈缓解）。
- 热点帖子计数更新竞争（热点拆分 / Redis 原子承载，见 §6）。
- 写透 DB 的事务提交与 Redis `HINCRBY` 非同源，存在秒级漂移窗口（对账 / TTL 收敛）。