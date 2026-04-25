# 工程记忆索引

## 文件说明

- [会话启动摘要](session-brief.md) — **SessionStart hook 读取**，项目宏观进度与渐进式读取策略
- [项目进度快照](project-progress.md) — 完整进度事实源，中等及以上任务、阶段规划和重大功能收口时读取
- [已学规则](learned-rules.md) — 13 条稳定规则（R-001~R-013）
- [纠正记录](corrections.md) — 5 条纠正（C-001~C-005），含 8 条反模式（A-001~A-008）
- [阶段观察](observations.md) — 8 条观察（O-001~O-008），含 13 条演化记录（E-001~E-013）

## 当前高频主题

- Phase 1 核心管线已完成，Phase 2 大部分完成
- 新会话只强制加载 `session-brief.md`，避免完整进度占用过多上下文
- 下一优先：聊天记忆 Redis 层、查询改写接入、语义分块、Cross-Encoder 重排序
- 文档加载已统一为 MinerU 方案
- 三端数据同步（PG + Milvus + ES）已实现
- 前端设计系统迁移完成（Ember + Genesis）
