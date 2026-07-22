## 1. PR 1 — 规格与领域决策

- [x] 1.1 提交领域词汇表、自适应周计划设计和三份 ADR
- [x] 1.2 提交 OpenSpec proposal、design、五项 capability spec 和分 PR 任务清单
- [x] 1.3 运行 `openspec validate add-adaptive-rolling-training-plans --strict` 和文档差异检查
- [x] 1.4 推送分支并创建以 `main` 为 base 的草稿 PR

## 2. PR 2 — 数据模型、迁移与备份

- [x] 2.1 对将修改的数据库、实体、Store 和备份符号执行 GitNexus upstream impact
- [x] 2.2 新增周期、日程、周草稿、场地重量档位、动作偏好和伤病例外实体
- [x] 2.3 将 SQLite 9 升级到 10，新增表、索引和 9→10 显式迁移
- [x] 2.4 为新实体实现 Store 的最小 CRUD 和事务支持
- [x] 2.5 将备份升级到版本 5并保持版本 4 可读
- [x] 2.6 添加数据库迁移、Store round-trip 和备份兼容设备测试
- [x] 2.7 运行聚焦设备测试、`git diff --check` 和 GitNexus change detection
- [x] 2.8 推送分支并创建以 PR 1 分支为 base 的草稿 PR

## 3. PR 3 — 纯规则引擎

- [x] 3.1 对现有训练总结和动作元数据符号执行 GitNexus upstream impact
- [x] 3.2 新增计划输入、候选、解释、冲突和趋势信号的纯 Kotlin 模型
- [x] 3.3 实现最近三次趋势、冷启动、单档加减重和首次试练规则
- [x] 3.4 实现场地器械、伤病排除、过滤例外和连续日肌群恢复校验
- [x] 3.5 添加覆盖边界、失败解释和 AI 候选不可绕过规则的 JVM 测试
- [x] 3.6 运行 JVM 测试、`git diff --check` 和 GitNexus change detection
- [x] 3.7 推送分支并创建以 PR 2 分支为 base 的草稿 PR

## 4. PR 4 — 滚动计划仓储工作流

- [x] 4.1 对计划生成、确认、状态聚合和 repository 符号执行 GitNexus upstream impact
- [x] 4.2 实现计划周期创建、下一周主动生成和输入快照 SHA-256
- [x] 4.3 将 AI 与本地候选统一接入本地约束校验和逐项解释
- [x] 4.4 实现草稿过期检查和整周原子确认
- [x] 4.5 实现周期结束、新周期预填和未完成周提前生成语义
- [x] 4.6 添加 repository 设备集成测试和规则边界测试
- [x] 4.7 运行聚焦测试、`git diff --check` 和 GitNexus change detection
- [ ] 4.8 推送分支并创建以 PR 3 分支为 base 的草稿 PR

## 5. PR 5 — 配置与可解释草稿 UI

- [ ] 5.1 对计划、设置、导航和根状态符号执行 GitNexus upstream impact
- [ ] 5.2 实现周期周数、训练星期、统一时长和逐日场地配置 UI
- [ ] 5.3 实现场地器械重量档位查看与编辑 UI
- [ ] 5.4 实现逐动作依据、冲突、过期状态、草稿编辑和整周确认 UI
- [ ] 5.5 实现一次性动作调整与持久动作偏好的选择
- [ ] 5.6 添加 Compose UI 测试并验证五主标签及 route/back 行为不变
- [ ] 5.7 运行聚焦 UI 测试、截图验证、`git diff --check` 和 GitNexus change detection
- [ ] 5.8 推送分支并创建以 PR 4 分支为 base 的草稿 PR

## 6. PR 6 — 训练反馈闭环与整体验证

- [ ] 6.1 对训练开始、记录组、结束训练和训练总结符号执行 GitNexus upstream impact
- [ ] 6.2 实现计划目标快照锁定和实际训练数据分离
- [ ] 6.3 实现四类提前结束原因和自由文本
- [ ] 6.4 实现身体不适后的伤病复核门禁
- [ ] 6.5 实现器械临时/长期不可用分流及场地状态更新
- [ ] 6.6 添加训练流程、伤病复核和器械更新的设备/Compose 测试
- [ ] 6.7 运行完整 Gradle gate、Pixel 8 Pro 真实流程、日志、SQLite 和截图验证
- [ ] 6.8 运行 `git diff --check`、OpenSpec strict validation 和 GitNexus change detection
- [ ] 6.9 推送分支并创建以 PR 5 分支为 base 的草稿 PR
