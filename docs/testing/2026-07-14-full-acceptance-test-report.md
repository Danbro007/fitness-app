# i fitness 全量验收与修复报告

## 1. 结论

2026-07-14 在 Pixel 8 Pro Android 16 模拟器（1170 × 2532，约 390 × 844 dp）完成 JVM、Compose 设备、lint、APK、授权媒体和真实 SQLite 验收。

- 可归因生产源码覆盖率：Class、Method、Branch、Line 均为 100%。
- 最终设备测试：165 个测试被发现，1 个需要外部百炼凭据的联网测试跳过，其余 164 个通过，0 失败。
- JVM 测试、lint、Debug APK、AndroidTest APK 全部构建成功。
- 授权媒体版 APK 安装和冷启动成功；未发现 FATAL EXCEPTION、ANR、OOM。
- 动作库实际入库 1324 条，器械 78 种；授权 GIF 在动作详情中完整显示。
- 模拟器本地资料已写入并核对：山崎杰、1994、176 cm、76.5 kg、保持体能、每周 3 天、45 分钟。

## 2. 覆盖率

执行命令：

```bash
./gradlew :app:verifyJacocoDebugCombinedCoverage
```

可归因源码门禁结果：

| 指标 | Covered | Missed | 结果 |
| --- | ---: | ---: | --- |
| Class | 107 | 0 | 100% |
| Source method | 497 | 0 | 100% |
| Source branch | 2127 | 0 | 100% |
| Source line | 5591 | 0 | 100% |

原始 JaCoCo 结果保留如下，用于审计编译器产物：

| 指标 | Covered | Missed |
| --- | ---: | ---: |
| Instruction | 48279 | 521 |
| Branch | 2193 | 36 |
| Line | 5591 | 0 |
| Method | 1231 | 70 |
| Class | 107 | 0 |

门禁排除了 804 个 Kotlin/Compose 生成方法和 102 个编译器生成分支。排除范围仅限匿名 lambda、属性 getter、默认参数桥接、Compose change-mask/state-restoration/semantics 适配字节码；每个仍映射到源码行的分支都要求同一行存在 `coverage-exempt: compiler-generated` 审计标记。门禁会拒绝未标记缺口和已经过期的标记。

报告位置：

- `app/build/reports/jacoco/debugCombined/html/index.html`
- `app/build/reports/jacoco/debugCombined/report.xml`
- `app/build/reports/jacoco/debugCombined/source-aware-summary.txt`

## 3. 已修复问题

1. 根导航同时注册训练返回处理器，形成不可达分支。现明确由训练页处理可恢复训练返回；根层只处理不可恢复训练并回首页。
2. 计划生成、草稿确认/重生成、计划保存存在控件禁用与回调内重复防抖。删除重复不可达判断，保留控件级防抖和协程状态。
3. GIF 解码器的 API 26/28 选择逻辑与实际工厂创建分离，曾触发 minSdk lint 错误。现由显式 API level 选择安全工厂，并覆盖两种实现。
4. 训练进行页 Activity 上下文使用可空返回加 `requireNotNull`。现由上下文解析函数提供明确非空契约，无 Activity 时给出确定异常。
5. 训练总结使用内联 `maxByOrNull`，标准库比较分支被映射到 UI 行且并列规则不直观。现提取纯函数，覆盖空集合、单项、较大值和并列值。
6. 补齐图片选择取消、头像选择取消、空备份、无场地瞬态、无效计划日期、仅完成训练日、空训练日、非法重量/次数输入等边界测试。
7. 修复新增测试中的模糊文本选择器，避免同名语义节点导致假失败。

## 4. 构建与静态检查

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

结果：成功。lint HTML 为 `app/build/reports/lint-results-debug.html`。

授权媒体版：

```bash
./gradlew :app:assembleDebug \
  -PincludeLicensedExerciseMedia=true \
  -PexerciseMediaLicenseReference=docs/compliance/exercisedb-personal-noncommercial-media-record.md
```

结果：成功。APK 为 `app/build/outputs/apk/debug/app-debug.apk`，约 148 MB。

## 5. 真实设备验收

| 检查项 | 实际结果 |
| --- | --- |
| 安装/冷启动 | 成功；显式冷启动约 1.2 秒 |
| 首页 | 设计布局、五标签、周节奏、快速入口正常 |
| 快速入口 | “记录饮食”可进入饮食页，无卡死/ANR |
| 训练页 | 无计划时显示确定空状态，不误启动训练 |
| 我的 | 显示山崎杰、保持体能、每周 3 练、45 分钟 |
| 动作库 | 显示 1324 个本地动作，中文分类和搜索入口正常 |
| 动作详情 | 授权 GIF 完整显示，无“本地 GIF”角标，无裁切问题 |
| SQLite | `exercise_media=1324`、`equipment=78`、资料与 onboarding 状态正确 |
| logcat | 未发现 FATAL EXCEPTION、ANR、OutOfMemoryError |

验收截图：

- `output/final-home-profiled.png`
- `output/final-profile.png`
- `output/final-library.png`
- `output/final-exercise-detail.png`

## 6. 限制与说明

- 百炼真实联网端到端测试需要外部凭据，本次按设计跳过；请求构造、解析、失败恢复、草稿确认和本地持久化均由离线测试覆盖。
- 当前本机只安装 Android 36 系统镜像；API 26/28 解码选择通过可注入 API level 的设备测试覆盖，但未在独立 API 26 AVD 上启动整包。
- GitNexus 对全部未提交改动给出 `critical`：37 个已索引文件、711 个变更符号、213 条受影响流程。该风险与跨数据、导航、UI、AI、媒体的大范围既有工作树改动一致；本报告中的全量回归覆盖这些流程，但提交前仍应按功能边界复核 diff。
