# R8-C 简历版本历史 / 时光机（快照 + diff 回放 / 回滚）

> 来源：`requirements.md` §4（R8 候选 C）
> 阶段：阶段四 | 难度：⭐⭐⭐ 中 | 依赖：`resume` 表、乐观锁 `version`、R6 diff 工具（可复用）

## 1. 定位

为简历提供**版本快照 + 历史回放 / 回滚**能力。每次保存（自动保存 / 手动保存 / AI 应用）生成一份不可变快照，用户可查看历史版本、对比差异（diff）、一键回滚到任意历史版本。既降低「改坏了回不去」的风险，也是订阅 / 绿色级增值点。

## 2. 功能点

1. **快照生成**：在 `resume` 保存（`PUT` / `PATCH` / AI apply）时，将变更后的 `ResumeDTO` JSON 序列化存入快照表（含 `version`、保存原因、时间）。
2. **历史列表**：按 `resume_id` 列出快照（时间 + 版本号 + 来源：手动/AI润色/AI扩写等）。
3. **版本对比（diff）**：任意两个版本 / 与当前版本的字段级 diff，高亮增删改（复用 R6 `resumeDiff.ts`）。
4. **回滚**：选择历史版本 → 恢复到该版本（写回 `resume`，乐观锁 `version` 递增，回滚本身也生成一条新快照，可再次回滚）。
5. **保留策略**：快照数量 / 保留时长上限（可配置），超限自动清理，控制存储与成本。

## 3. 数据模型

```sql
CREATE TABLE resume_snapshot (
  id          BIGINT   NOT NULL AUTO_INCREMENT,
  user_id     BIGINT   NOT NULL COMMENT '冗余隔离',
  resume_id   BIGINT   NOT NULL,
  version     INT      NOT NULL COMMENT '对应 resume.version',
  source      VARCHAR(32) NOT NULL DEFAULT 'manual' COMMENT 'manual/ai_rewrite/ai_expand/ai_apply',
  content     JSON     NOT NULL COMMENT 'ResumeDTO 完整快照',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_resume (user_id, resume_id),
  UNIQUE KEY uk_resume_version (resume_id, version)
) ENGINE=InnoDB;
```

- `content` 用 `JSON` 存整份 `ResumeDTO`，rollback 时反序列化回写 `resume`。
- `user_id` 冗余 + 强制过滤（隔离红线）；`version` 与 `resume.version` 对齐保证唯一。

## 4. 接口（`/api/resumes/{resumeId}/history`，登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/resumes/{resumeId}/history` | 历史快照列表 |
| GET | `/api/resumes/{resumeId}/history/{snapshotId}` | 快照详情（完整内容） |
| GET | `/api/resumes/{resumeId}/diff?from=&to=` | 两个版本字段级 diff |
| POST | `/api/resumes/{resumeId}/rollback/{snapshotId}` | 回滚到指定快照 |

- 全部强制 `user_id` 过滤（跨用户 404/403）。
- 回滚复用既有保存链路（乐观锁 `version`），并发冲突返回 `409`。

## 5. 前端改动

- 编辑器 `Toolbar` 新增「历史版本」入口 → `ResumeHistoryModal`（或侧栏）：时间轴列出快照、点击预览、`diff` 高亮对比、一键回滚。
- `api/resumes.ts` 新增 `listHistory` / `getHistory` / `diffResumeVersions` / `rollback`；复用 `resumeDiff.ts` 的 diff 组件。

## 6. 验收标准

- 每次保存 / AI 应用均有对应快照，列表与 `version` 一一对应，跨用户不可见。
- 两版本 diff 字段级高亮正确，回滚后内容与所选版本一致，且回滚本身可再次被回滚。
- 快照超上限按策略清理；并发回滚冲突返回 `409`。

## 7. 难点与风险

- **存储膨胀**：整份 JSON 全量快照会随版本线性增长，需保留策略（数量 / 天数）与可配置开关；进阶可做「增量 / 懒加载」。
- **diff 粒度**：复用 R6 的按字段 diff（`resumeDiff.ts`），避免整块 JSON 文本对比不可读。
- **回滚语义**：回滚产生新快照（不覆盖历史）保证可逆；与乐观锁 `version` 的交互需在事务内原子完成。
- **一致性**：快照写入应随 `resume` 保存同事务提交，避免「保存成功但快照缺失」。