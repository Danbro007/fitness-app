# i fitness 全量验收与 100% 覆盖率测试计划

- 版本：1.0
- 日期：2026-07-14
- 测试对象：原生 Android App；HTML 原型仅作为视觉基线
- 需求基线：`docs/superpowers/specs/2026-07-11-futuristic-neumorphism-ui-development-requirements.md`
- 视觉基线：`.scratch/futuristic-neumorphism-prototype/i-fitness-未来主义新拟态交互原型.html`
- 设备基线：Pixel 8 Pro AVD，1170 × 2532 px，480 dpi，390 × 844 dp

## 1. 目标

1. 从干净安装和已有本地数据两种状态重新打开 App，完成 22 个页面/状态和关键弹层的真实用户验收。
2. 验证本地优先、无账号、无云同步、SQLite 为事实源、AI 草稿确认后落库等产品边界。
3. 对发现的缺陷执行“失败测试 → 最小修复 → 回归测试”的 TDD 闭环。
4. 对可归因的生产源码达到 100% 行覆盖率、100% 分支覆盖率和 100% 方法覆盖率。
5. 提供可复核的命令、覆盖率报告、截图、logcat、SQLite 与 APK 证据。

## 2. 测试接缝

测试只通过以下公共边界观察行为，不测试私有实现：

| 接缝 | 观察方式 | 主要风险 |
| --- | --- | --- |
| Compose 页面与导航 | 可见文字、语义、点击、输入、系统返回、Activity 重建 | 路由错误、按钮失效、遮挡、状态不同步 |
| `FitnessRepository` 公共 API | 输入业务命令并收集公开状态 | 事务、草稿确认、并发、离线兜底 |
| SQLite 与备份 | 通过 Store/Repository 写入读取、迁移、导入导出 | 丢数据、重复写、旧版本不兼容 |
| AI 网关与请求构造 | 可控 transport、凭证存储、公开草稿结果 | 敏感信息泄露、Markdown 裸露、无网阻塞 |
| 已安装 APK | 用户可见点击、logcat、SQLite、截图、进程状态 | ANR、崩溃、性能、仅测试环境通过 |

## 3. 范围

### 3.1 功能范围

- 首次设置、档案、头像、体测、训练偏好。
- 首页唯一主任务、快捷入口、五个一级导航和来源感知返回。
- 周/月/年训练日历、日期详情、计划创建/编辑/删除、四周 AI 草稿。
- 动作库中文搜索、部位/器械筛选、动作详情、GIF、收藏、加入计划/训练。
- 训练准备、执行、数值输入、体感、休息、暂停、恢复、提前结束、完整结束、AI 总结和未来计划调整。
- 饮食汇总、手动记录、照片回显、AI 热量估算草稿、确认/放弃。
- OpenAI、Gemini、千问设置、Keystore、连接失败、无凭证本地兜底。
- 本地备份、旧版本导入、头像恢复、损坏备份回滚、重置数据。
- 生命周期、并发点击、离线、中文输入、无障碍、对比度、性能和稳定性。

### 3.2 非范围

- 未经授权的云账号、服务端同步和后台上传。
- 第三方 AI 服务自身的正确率或可用性；只验证请求契约、失败体验和显式凭证下的可选联调。
- 第三方 GIF 内容本身的动作医学正确性。

## 4. 环境与构建

| 项目 | 配置 |
| --- | --- |
| JVM | Gradle Wrapper 所使用的 JDK 17 |
| Android | API 36 Pixel 8 Pro AVD；补充 minSdk API 26 兼容验证 |
| 默认 APK | 不包含第三方 GIF 二进制，验证授权门禁 |
| 个人媒体 APK | 同时传入 `includeLicensedExerciseMedia` 与权利记录属性 |
| 网络 | 飞行模式/断网、无 API Key、无效 API Key、显式有效测试 Key 四种状态 |
| 数据 | 干净数据库、完整样例档案、进行中训练、历史训练、未来计划、旧备份 |

媒体版命令：

```bash
./gradlew :app:assembleDebug \
  -PincludeLicensedExerciseMedia=true \
  -PexerciseMediaLicenseReference=docs/compliance/exercisedb-personal-noncommercial-media-record.md
```

## 5. 覆盖率口径

### 5.1 分母

纳入 `app/src/main/java` 中所有可归因到 Kotlin/Java 生产源码的可执行行、条件分支和方法，包括正常路径、校验失败、异常恢复、事务回滚与离线兜底。

仅排除以下非业务产物：

- `R`、`R$*`、`BuildConfig`、Manifest 生成类。
- Kotlin/Compose 编译器生成的 `$DefaultImpls`、`$WhenMappings`、`ComposableSingletons$*`、匿名 lambda/continuation/synthetic accessor。
- JaCoCo 无法映射回生产源码行的生成字节码。

不得为提高百分比排除 Repository、Store、数据库迁移、UI 页面、导航、AI、图片、备份或错误分支。任何新增排除项必须在最终报告中逐条说明理由。

### 5.2 门槛

| 指标 | 最终门槛 |
| --- | ---: |
| Source line | 100% |
| Source branch | 100% |
| Source method | 100% |
| Source class | 100% |

JVM 与设备覆盖率必须合并后计算；不能用单个测试类或仅新增代码的覆盖率代替全量结果。HTML 报告、XML 报告和门禁任务都必须生成。

## 6. 执行顺序

1. 静态清单：核对 22 个页面/状态、路由、需求和现有测试映射。
2. JVM 快速门禁：纯规则、导航、格式化、AI 请求、解析与边界。
3. Repository/SQLite 设备测试：迁移、事务、备份、Keystore、并发和离线兜底。
4. Compose UI 设备测试：页面、导航、中文输入、状态、Activity 重建和视觉语义。
5. 生成 JVM + 设备合并覆盖率；按未覆盖行和分支逐个补测试。
6. 覆盖安装媒体版 APK；按真实点击用例验收并检查 logcat、SQLite 和截图。
7. API 26 运行关键启动/GIF/数据库流程。
8. 运行完整 Gradle 门禁、`git diff --check` 和 GitNexus 变更影响分析。
9. 输出最终报告，逐项对照需求和用例；任一证据缺失则不判定完成。

## 7. 进入与退出条件

### 进入条件

- 模拟器在线且分辨率/dpi 符合基线。
- 默认版和媒体版均可构建。
- 测试数据不包含真实 API Key 或未授权媒体副本。
- 当前 dirty worktree 已记录，测试不得覆盖用户现有改动。

### 退出条件

- `docs/testing/2026-07-14-full-acceptance-test-cases.md` 中 P0/P1 用例全部通过，P2 无未解释失败。
- 22 个页面/状态和必需弹层均有真实原生证据。
- 行、分支、方法、类覆盖率全部为 100%。
- JVM、Lint、APK、AndroidTest、安装、启动、真实点击、logcat 和 SQLite 验证全部通过。
- 无 FATAL EXCEPTION、ANR、数据丢失、重复写入、明文 Key 或 AI 未确认落库。
- 所有缺陷均具备失败测试和修复后回归证据。

## 8. 缺陷等级

| 等级 | 定义 | 处理 |
| --- | --- | --- |
| P0 | 崩溃、ANR、数据损坏、密钥泄露、无法进入核心流程 | 立即停止其他验收并修复 |
| P1 | 核心按钮无响应、错误写入、训练/计划/饮食主流程错误 | 本轮必须修复 |
| P2 | 明显布局、可读性、返回、输入或无障碍问题 | 本轮修复并回归 |
| P3 | 不影响任务完成的小型视觉偏差 | 记录并在完成前评估 |

## 9. 证据要求

每条真实设备用例至少记录：前置数据、操作步骤、实际结果、关键截图、logcat 结论；涉及写入时还需记录 SQLite/Repository 状态。覆盖率报告必须保留任务命令、HTML/XML 路径、总计和按文件缺口。失败用例必须保留最短复现路径和红灯测试名称。
