# i fitness 原生 Android 布局迁移设计

## 1. 目标与依据

将已经验收的 `健身App-单一主任务交互原型.html` 迁移到真实 Android Compose 应用。迁移后的 APK 必须继续使用现有 SQLite、Repository、本地 GIF、训练记录、饮食记录、档案、AI 草稿和备份能力，不以纯 UI 假状态替代真实数据。

视觉与交互以 `docs/superpowers/specs/2026-07-10-interactive-layout-prototype-design.md` 和已验收截图为准。仓库没有所属 OpenSpec 根目录；相邻目录的 `openspec/` 与本项目无关，因此本设计和后续实施计划作为本次变更的权威文档。

## 2. 方案比较与决策

### 方案 A：继续在 `MainActivity.kt` 内换皮

优点是改动文件少、短期编译快。缺点是当前文件已接近 3900 行，七栏导航、业务调用和视觉组件耦合；训练休息只存在于 `remember`，不能满足恢复和总结要求。否决。

### 方案 B：只拆 Compose 页面，数据层保持不变

可以快速得到五栏截图，但 `completeSet()` 仍可能写孤立日志，计划外动作无法真正加入训练，休息和当前动作无法恢复。它会制造“看起来完成”的假象。否决。

### 方案 C：按垂直流程迁移 Compose，并补齐训练运行态

推荐并采用。先建立可测试的五栏导航和 UI 状态契约，再补 Repository/SQLite 的真实训练状态，最后逐页迁移首页、训练、计划/动作库、饮食、我的。每个阶段都能独立构建、测试和安装，不重写已有本地业务能力。

## 3. 信息架构与路由

底部导航固定为五项，顺序不可改变：

1. 首页
2. 计划
3. 训练
4. 饮食
5. 我的

动作库、动作详情、计划详情/编辑、场地与器械、智能设置、数据备份、关于均为二级路由。AI 周计划入口归入计划，照片估算归入饮食，动作替换归入训练，API 配置归入我的。

导航使用项目内 sealed route + `AnimatedContent`，不新增 Navigation Compose 依赖。一级 Tab、二级路由、选中计划/动作使用 `rememberSaveable`；训练运行态必须写 SQLite，不能只依赖 Compose 状态。

训练准备是训练 Tab 的默认页面。进入训练中后隐藏全局底栏；总结页同样隐藏底栏。系统返回在训练中必须先弹出确认，不允许静默丢弃运行态。

## 4. 模块边界

```text
MainActivity.kt                         Activity 与 setContent
ui/FitnessAppRoot.kt                    bootstrap、appState 收集、根路由
ui/navigation/FitnessNavigation.kt     PrimaryTab、AppRoute、NavState、五栏底栏
ui/model/FitnessUiModels.kt             首页、训练、总结等纯 UI 派生模型
ui/theme/FitnessTheme.kt                米白/橙/深色/绿色 token 与 typography
ui/components/FitnessComponents.kt      通用按钮、卡片、标题、GIF、指标
ui/home/HomeScreen.kt                   单一主任务首页
ui/plan/PlanScreens.kt                  周计划、详情、编辑、草稿
ui/training/TrainingScreens.kt          准备、进行、休息、确认、总结
ui/library/LibraryScreens.kt            搜索、筛选、详情、加入计划/训练
ui/food/FoodScreens.kt                  汇总、时间线、添加餐弹层、照片草稿
ui/profile/ProfileScreens.kt            摘要、编辑、设置列表与二级设置页
```

旧 `MainActivity.kt` 中已迁移的 private Composable 在新根组件切换稳定后删除，避免两套 UI 继续漂移。Repository、Store、Entities 继续放在现有 data 包。

## 5. 数据与运行态

### 5.1 新装状态

停止在 bootstrap 时创建虚假的 `in_progress` 默认 session。无真实未结束训练时，首页主操作为“开始训练”；存在运行中 session 时为“继续训练”；最近训练完成后可进入“查看训练总结”。

### 5.2 一次训练一个 Session

新训练使用一个 session ID，包含多个 session exercise。数据库版本从 6 升至 7：

- `workout_session` 增加 `current_exercise_id`、`rest_ends_at`、`paused_at`。
- 新增 `workout_session_exercise`：session、exercise、顺序、目标组/次数/重量、状态。
- `workout_set_log` 增加 `session_exercise_id`，新数据按 session exercise 记录。
- 新增 `(session_id, session_exercise_id, set_index)` 唯一约束；历史无关联日志继续可读。

运行态统一由 Repository 提供：准备、开始、选择动作、记录本组、开始/跳过休息、跳过动作、暂停/恢复、结束、总结。`recordSet` 必须确保 session 存在、校验输入和目标组上限，并在同一事务中写入。

### 5.3 计划与动作库

新增向计划添加动作、移除动作和调整顺序的 Repository API。动作详情的“用于本次训练”根据来源执行：

- 从训练准备进入：加入当前 session/preparation。
- 从计划编辑进入：加入计划并初始化目标。
- 从首页进入：打开默认计划的训练准备，不能只切换 UI 选中 ID。

Raw exercise 字段保持不变；所有中文显示继续经过 translator layer。

### 5.4 饮食、档案与设置

饮食汇总由当天真实 `FoodLogEntity` 计算；手动记录和照片估算均在确认后落库。照片估算继续使用现有本地草稿/已配置 provider 逻辑，不新增云账号。

“我的”首屏只读。编辑资料、场地与器械、智能设置、数据备份和关于进入二级页面。智能状态只从 API Key/Provider 的单一真实来源派生，不能同时出现“已连接”和“密钥未保存”。重置本地数据必须二次确认，并清除个人数据和本机凭据后重新初始化基础计划/动作索引，不重建虚假训练 session。

## 6. 页面行为

### 首页

只显示一个训练主操作。深色 Hero 显示当前计划、动作数、预计时长、完成组数和本地 GIF；下方只保留本周完成次数、周视图、记饮食和动作库两个轻量入口。不显示首次配置卡、恢复训练卡、训练队列或资产诊断。

### 计划

周视图和本周安排优先，休息日明确显示“休息日”；月计划生成在下方并先产生草稿。计划详情/编辑继续使用现有真实 CRUD，动作库可向计划添加动作。

### 训练

训练准备显示计划、预计时长和动作列表。训练中使用深色沉浸页面，底栏隐藏，4:3 GIF、动作 chips、重量/次数/体感和固定“完成本组”在 390×844 等效屏幕首屏可见。完成一组进入基于 `restEndsAt` 的休息状态；跳过或倒计时结束后进入下一组，完成当前动作目标后切换下一动作。提前结束需要确认。总结展示完成组数、总容量、时长、体感分布和本周进度。

### 饮食

顶部展示热量、蛋白质、碳水、脂肪真实汇总，只有一个“添加一餐”主入口。底部弹层选择拍照估算或手动记录；输入错误就地提示并保留内容。

### 我的

展示只读档案和训练指标；下方为设置列表。只有编辑资料页出现输入框。备份/导入和重置均在独立二级页操作。

## 7. 视觉与无障碍

- 使用暖米白背景、深墨文字、橙色主操作、深色训练面、绿色进度/完成/选中。
- 蓝色不承担主状态；未配置/普通信息使用中性色。
- 大模块 24dp、普通容器 16dp、按钮和输入 14–16dp 圆角。
- 所有交互目标至少 48dp；正常正文对比度至少 4.5:1。
- Material Icons Rounded 使用现有 Compose icon 依赖。
- meaningful GIF 有中文 contentDescription；装饰 icon 为 null；icon-only 按钮必须有描述。
- 尊重系统减少动态效果：动画不传递唯一状态信息。

## 8. 错误处理与恢复

- GIF 加载失败仍保留动作名和训练控制。
- SQLite/导入/AI/图片错误通过 Snackbar 或字段错误显示，不清空用户输入。
- 休息倒计时使用时间戳恢复；时间到后自动结束休息。
- 旧数据库迁移保留有日志的历史 session；只移除“默认 ID + 进行中 + 零日志”的虚假种子。
- 旧备份继续可导入；新增数据使用默认值补齐。

## 9. 测试与验收

采用 TDD：每个行为先增加失败测试，再实现。验证层次：

1. JVM 单元测试：导航顺序、首页 CTA 派生、周进度、训练 reducer、输入校验、总结计算。
2. Repository instrumented tests：数据库 v6→v7、开始/记录/休息/恢复/结束、动态动作、重置、备份兼容。
3. Compose UI tests：五栏顺序与唯一主操作；计划层级；训练中隐藏底栏并显示完成按钮；饮食汇总和弹层；我的摘要优先；二级返回和无障碍 tag。
4. 真实设备闭环：`testDebugUnitTest`、`connectedDebugAndroidTest`、`assembleDebug`、安装、启动、逐页 UIAutomator/截图、日志和 SQLite 检查。

完成标准不是“代码编译”：必须在 `Pixel_8_Pro / emulator-5554` 上看到五栏新版布局，完成至少一组训练并在 DB/总结/首页周进度中一致，饮食新增后汇总一致，设置页状态一致，且 APK 运行无崩溃。

## 10. 非目标

- 不增加账号、云同步、社交或新的后端。
- 不把 AI 输出直接写入正式数据；仍需确认。
- 不修改 1324 个 raw exercise/API 测试数据的原始值。
- 不重做动作素材或引入远程图片。
