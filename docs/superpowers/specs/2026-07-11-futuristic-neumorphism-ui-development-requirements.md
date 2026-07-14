---
name: i fitness 未来主义新拟态 UI 开发需求
description: 将已确认的未来主义新拟态交互原型迁移到原生 Android，并定义训练日历、档案体测、AI 服务商、AI 计划与头像能力的开发验收基线。
version: 1.0.0
last_updated: 2026-07-11
maintained_by: shanqijie
repo_prompt_name: i fitness Android App
repo_id: i-fitness-android
module_name: Mobile Product Experience
module_id: mobile-product-experience
feature_name: Futuristic Neumorphism Product Upgrade
feature_id: futuristic-neumorphism-product-upgrade
feature_aliases:
  - 未来主义新拟态 UI
  - 黑白荧光黄 UI
  - 训练日历与 AI 档案升级
related_docs:
  - ./2026-07-10-interactive-layout-prototype-design.md
  - ./2026-07-10-android-layout-migration-design.md
  - ../plans/2026-07-11-body-composition-ai-plan.md
---

# i fitness 未来主义新拟态 UI 开发需求

## 1. 文档定位

本文是本轮产品调整的开发需求基线。开发、测试和验收均以本文为准；此前文档与本文冲突时，以本文为准。

视觉与交互参考原型：

```text
.scratch/futuristic-neumorphism-prototype/i-fitness-未来主义新拟态交互原型.html
```

原型已经在浏览器中验证交互和布局，但不代表 Android 功能已经实现。Android 交付必须连接真实 SQLite、Repository、Keystore、图片存储和训练数据，禁止用静态假数据冒充完成。

## 2. 产品目标

1. 将 App 统一为“黑白荧光黄的未来主义新拟态 UI”。
2. 保持五项中文一级导航：首页、计划、训练、饮食、我的。
3. 让用户同时查看历史训练和未来计划，并支持周、月、年三个维度。
4. 让训练偏好与体测数据全部可编辑、持久化，并参与 AI 训练计划生成。
5. 支持 OpenAI、Gemini、千问（阿里云百炼）三类 AI 服务商，地址和模型通过下拉框选择。
6. 支持本地头像上传、更换、预览、备份与恢复；头像不得发送给 AI。
7. 保持本地优先、无账号、无默认云同步、AI 结果确认后落库的产品边界。

## 3. 非目标

- 不增加账号、社交、排行榜或云同步。
- 不把头像、训练记录或体测数据上传到自建服务端。
- 不允许 AI 直接覆盖正式训练计划；AI 只能生成待确认草稿。
- 不修改 1324 个动作的 raw API/test data；中文展示继续使用 translator layer。
- 不恢复“身体年龄”字段到可见 UI、AI 输入或新备份数据。
- 不把浏览器 `localStorage` 方案照搬到 Android。

## 4. 统一视觉与交互规范

### 4.1 视觉方向

| 项目 | 需求 |
| :--- | :--- |
| 风格 | Futuristic Minimalism + Neumorphism + Bento + 轻 Claymorphism |
| 主色 | 黑、白/暖灰 |
| 唯一强调色 | 荧光黄 |
| 形态 | 大圆角卡片、胶囊按钮、圆形图标、柔和凸起/凹陷阴影 |
| 深色面 | 首页训练 Hero、训练执行、休息倒计时 |
| 状态表达 | 必须同时使用文字或图标，不可只依赖颜色 |

### 4.2 尺寸与无障碍

- 所有触控目标不小于 `48dp × 48dp`。
- 普通正文对比度不低于 `4.5:1`。
- 支持 375dp、390dp、430dp 宽度及横屏，无横向滚动。
- icon-only 按钮必须有中文 `contentDescription`。
- 支持系统字体缩放，核心数值与按钮不得重叠或截断。
- 尊重减少动态效果设置；动画不能承担唯一状态信息。

### 4.3 已确认的视觉修正

- 首页动作图片不得旋转或压住主按钮；图片底边水平，与荧光黄按钮保持稳定间距。
- 休息倒计时和训练总结不使用粗圆环。
- 倒计时采用独立数字卡片和横向进度条，数字不得与进度图形重叠。
- 训练总结采用数值卡片和横向完成进度条。
- 页面菜单和操作文案全部使用中文。

## 5. 页面与路由范围

### 5.1 一级导航

| 顺序 | 页面 | 职责 |
| :---: | :--- | :--- |
| 1 | 首页 | 今日唯一训练任务、本周节奏、快捷入口 |
| 2 | 计划 | 训练日历、计划详情、编辑和 AI 草稿 |
| 3 | 训练 | 训练准备、执行、休息和总结 |
| 4 | 饮食 | 今日营养、手动记录和照片估算草稿 |
| 5 | 我的 | 档案摘要、编辑、场地、AI、备份和关于 |

### 5.2 必须覆盖的页面

开发不得遗漏以下页面或状态：

1. 首次训练设置
2. 首页
3. 训练日历
4. 计划详情
5. 计划编辑
6. 四周 AI 计划草稿
7. 训练准备
8. 训练执行
9. 组间休息
10. 训练总结
11. 动作库
12. 动作详情
13. 饮食首页
14. 拍照估算
15. 照片估算草稿
16. 手动记录饮食
17. 我的
18. 训练偏好与体测
19. 场地与器械
20. AI 设置
21. 数据备份
22. 关于

## 6. 首页需求

### FR-HOME-01 今日唯一任务

- 首页只显示一个主训练 CTA：开始训练、继续训练或查看总结。
- Hero 展示计划名、动作数量、目标组数、预计时长和代表动作图。
- 图片容器保持水平，不允许通过旋转制造装饰效果。
- CTA 使用整宽荧光黄胶囊按钮，圆形箭头垂直和水平居中。

### FR-HOME-02 快捷入口

- 保留“记录饮食”和“动作库”两个快捷入口。
- 首页展示简洁本周节奏，但完整历史与未来计划归入“计划”。

## 7. 训练日历需求

### 7.1 时间维度

训练日历必须提供“周 / 月 / 年”三个可切换 Tab，并保存用户最后选择的维度。

| 维度 | 展示内容 | 交互 |
| :--- | :--- | :--- |
| 周 | 历史已完成、今天、恢复日、近期未来计划 | 点击整行查看当天详情 |
| 月 | 正确月份天数与星期偏移；已完成、今天、已计划 | 每一天均可点击 |
| 年 | 12 个月的完成次数、计划次数和历史/本月/未来状态 | 点击月份进入对应月视图 |

### 7.2 日期详情

- 点击有训练的日期，打开底部详情面板。
- 详情展示：日期、训练名称、状态、预计/实际时长、总组数、动作名称、组数、次数、重量。
- 点击今天的待训练日期，可进入对应训练准备页。
- 点击无训练日期，展示“休息日 / 当天没有训练安排”，不得出现空白面板。
- 历史日期读取真实 `workout_session` 与 `workout_set_log`。
- 未来日期读取真实计划与计划动作，不得用历史数据伪造。

### 7.3 年到月跳转

- 点击年视图任意月份，必须切换到月维度并定位该月份。
- 月标题、天数、星期起始位置、完成数量和计划数量必须同步更新。
- 从月视图返回年视图后，年统计不得变化或丢失。

### 7.4 数据口径

| 状态 | 判定 |
| :--- | :--- |
| 已完成 | 当日存在已完成训练 Session |
| 今天 | 本地时区当天 |
| 已计划 | 当日存在未开始的正式计划 |
| 恢复日 | 正式计划明确标记恢复，或用户创建恢复项 |
| 无安排 | 无 Session、无计划、无恢复项 |

## 8. 训练执行需求

- 训练中隐藏全局底栏。
- 显示当前动作、动作 GIF、完成组数、重量、次数和体感。
- 完成本组后进入休息页；休息时间通过时间戳持久化，可恢复。
- 休息页显示独立倒计时数字、秒单位、横向进度条、下一组信息和跳过按钮。
- 提前结束训练必须确认；已完成组保存，未完成动作不得补记。
- 总结页展示完成组数、目标组数、训练容量、训练时长和主观体感。
- 训练保存后，用户可补充训练后感受和备注；AI 复盘输入必须包含每个动作的实际组数、次数、重量、组间体感、训练完成度和训练后感受。
- AI 复盘需要明确给出“加量、保持或减量”的后续计划判断；未连接 AI 服务时使用相同数据生成本地规则总结，不能阻塞训练保存。
- AI 复盘和计划调整都先保存为草稿。只有用户确认后，才能修改未来已计划训练中相同动作的目标重量或组数；用户可以选择保持原计划。
- 疼痛或明显疲劳优先给出减量和专业评估提示，不允许自动加量。

## 9. 训练偏好与体测

### 9.1 全部可编辑字段

以下字段均需支持编辑、校验、保存、重新进入回显和备份恢复：

| 分组 | 字段 | 类型/单位 | 建议校验 |
| :--- | :--- | :--- | :--- |
| 基础 | 昵称 | String | 1–30 字符 |
| 基础 | 出生年 | Int | 1940–当前年份 |
| 基础 | 身高 | Double / cm | 80–240 |
| 基础 | 体重 | Double / kg | 25–250 |
| 偏好 | 训练目标 | Enum | 增肌、减脂、保持体能 |
| 偏好 | 每周训练天数 | Int | 1–7 |
| 偏好 | 单次训练分钟 | Int | 15–180 |
| 偏好 | 伤病/注意事项 | String | 0–500 字符 |
| 体测 | 体脂率 | Double / % | 0–75 |
| 体测 | 体脂肪 | Double / kg | 0–150 |
| 体测 | BMI | Double | 10–80；按用户录入值保存 |
| 体测 | 骨骼肌 | Double / kg | 0–100 |
| 体测 | 身体水分 | Double / kg | 0–150 |
| 体测 | 基础代谢 | Int / kcal | 500–5000 |
| 体测 | 腰臀比 | Double | 0.3–2.0，显示两位小数 |

### 9.2 明确移除

- “身体年龄”不出现在编辑页、摘要页、AI Prompt 或新备份结构中。
- 为兼容旧数据库，历史 `body_age` 列可以保留，但新代码不得读取为业务输入或继续写入。
- 旧备份中的 `bodyAge` 字段导入时忽略，不应导致导入失败。
- 旧“体型”与“体测日期”不在本轮必需 UI 中；若保留历史数据，不得计入本文所称 15 项 AI 输入。

### 9.3 保存体验

- 输入错误在对应字段附近显示，保留已输入内容。
- 保存期间按钮禁用并显示“保存中…”。
- 保存成功后显示 Snackbar，并更新“我的”摘要。
- 小数不得被隐式取整；腰臀比保持两位显示。

## 10. 头像上传

### FR-AVATAR-01 文件选择

- “我的”头像可点击进入编辑页。
- 编辑页提供上传/更换头像入口。
- 支持 JPG、PNG、WebP，最大 5 MB。
- 使用 Android Photo Picker；不申请广泛媒体读取权限。

### FR-AVATAR-02 处理与存储

- 选择后居中裁剪为正方形，最长边压缩到 512px 以内。
- 输出 JPEG/WebP，质量建议 80–88%。
- 文件保存到 App 私有目录，数据库只保存内部相对路径或稳定 URI，不保存外部临时 URI。
- 新头像写入成功后再删除旧头像，避免失败导致头像丢失。
- 无头像时用昵称前 1–2 个字符作为占位。

### FR-AVATAR-03 隐私与备份

- 头像只保存在本机，不进入 AI Prompt，不发送给 AI 服务商。
- 本地 JSON 备份应携带压缩后的头像数据或独立头像文件；恢复后必须可显示。
- 导入损坏头像时跳过头像并恢复其余档案，不得让整个备份失败。

## 11. AI 服务商设置

### 11.1 服务商范围

首期只支持以下服务商：

| Provider ID | 展示名 | Logo | 接口地址 | 模型 |
| :--- | :--- | :--- | :--- | :--- |
| `openai` | OpenAI | 官方 Logo | 下拉选择，默认官方 `/v1` | 下拉选择 GPT 系列配置项 |
| `gemini` | Gemini | 官方 Logo | 下拉选择 Gemini OpenAI 兼容地址 | 下拉选择 Gemini 配置项 |
| `qwen` | 千问 | 阿里云百炼/Qwen Logo | 北京、新加坡、美国区域下拉 | 下拉选择 Qwen 配置项 |

首版内置目录与原型保持一致，后续允许通过配置升级：

| Provider | Endpoint value | Model value |
| :--- | :--- | :--- |
| OpenAI | `https://api.openai.com/v1` | `gpt-5-mini`、`gpt-5`、`gpt-4.1-mini` |
| Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-3.5-flash` |
| 千问（北京） | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen3.7-plus`、`qwen3.7-max`、`qwen3.6-flash` |
| 千问（新加坡） | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | 同千问模型目录 |
| 千问（美国） | `https://dashscope-us.aliyuncs.com/compatible-mode/v1` | 同千问模型目录 |

模型目录属于可更新配置，不应通过数据库迁移才能调整；更新目录时必须保留已保存但暂时不在目录中的模型值，并提示用户重新选择。

### 11.2 表单规则

- 服务商使用带 Logo 的选择卡，不要求用户记忆地址。
- 接口地址和模型都必须使用下拉框，不允许自由文本作为主路径。
- 切换服务商后，地址和模型下拉选项同步切换到该服务商目录。
- API Key 使用 Android Keystore + AES/GCM，禁止明文写入 SQLite、日志或备份。
- 测试连接需要展示成功、失败、超时和无网络状态。
- Provider、endpoint、model 目录使用配置对象维护，避免散落在 Composable 中。

## 12. AI 训练计划数据契约

### 12.1 触发条件

- 只有用户主动点击“生成训练计划”才构建并发送 AI 请求。
- 未连接 AI 时可生成明确标记的本地兜底草稿。
- AI 返回内容先保存为草稿；用户点击“确认采用”后才写入正式计划。

### 12.2 必须进入 AI Prompt 的 15 项档案

1. 昵称
2. 出生年
3. 身高
4. 体重
5. 训练目标
6. 每周训练天数
7. 单次训练分钟
8. 伤病/注意事项
9. 体脂率
10. 体脂肪
11. BMI
12. 骨骼肌
13. 身体水分
14. 基础代谢
15. 腰臀比

同时加入：当前训练场地、可用器械、近期训练历史摘要。头像不得加入。

### 12.3 缺失值规则

- 可选体测为空时，Prompt 明确标记“未填写”，不得伪造默认值。
- 基础必填字段缺失时禁止发起请求，并引导用户补全档案。
- Prompt 使用结构化 JSON 或有固定字段名的文本模板，便于测试字段覆盖。
- AI 请求日志只能记录字段名和调用状态，不记录 API Key、头像或完整敏感值。

### 12.4 发送前可见

AI 草稿页在计划内容之前展示“本次 AI 输入”快照：

- 展示上述 15 项档案和单位。
- 明确说明“全部档案数据已参与”。
- 训练频率和单次时长必须真实影响草稿中的每周次数和单次时长。
- AI 设置页的数据边界写明“发送训练偏好、全部体测数据与器械约束”。

## 13. 数据模型与迁移要求

### 13.1 `user_profile`

在现有字段基础上增加：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `bmi` | REAL NULL | 用户可编辑并持久化 |
| `avatar_path` | TEXT NOT NULL DEFAULT '' | App 私有目录相对路径 |

`body_age` 保留为 legacy 列，不再写入。若当前数据库版本为 N，则升级到 N+1，并提供真实旧库迁移测试。

### 13.2 Kotlin 实体

- `BodyMeasurement` 增加 `bmi: Double?`。
- `BodyMeasurement.bodyAge` 标记 deprecated 或从新业务模型移除；序列化兼容旧备份。
- `UserProfileEntity` 增加 `avatarPath: String = ""`。
- `FitnessBackupCodec` 支持 BMI 和头像，忽略旧 `bodyAge`。

### 13.3 日历查询

Repository 提供统一查询结果，UI 不直接拼 SQL：

```kotlin
data class CalendarDaySummary(
    val date: LocalDate,
    val status: CalendarDayStatus,
    val workoutName: String?,
    val completedSets: Int,
    val plannedSets: Int,
)
```

至少提供：

- `calendarWeek(anchorDate)`
- `calendarMonth(yearMonth)`
- `calendarYear(year)`
- `calendarDayDetail(date)`

## 14. Android 代码影响范围

| Area | 当前路径 | 主要改动 |
| :--- | :--- | :--- |
| 根路由 | `ui/FitnessAppRoot.kt` | 新增/衔接日历详情、AI 草稿、头像流程 |
| 计划 UI | `ui/plan/PlanScreens.kt` | 周/月/年、月份跳转、日期详情、AI 输入快照 |
| 档案 UI | `ui/profile/ProfileScreens.kt` | 新视觉、全部字段、移除身体年龄、头像 Picker |
| AI 设置 UI | `ui/settings/SettingsScreens.kt` | 三服务商、Logo、endpoint/model 下拉、数据边界 |
| 数据实体 | `data/FitnessEntities.kt` | BMI、头像路径、legacy bodyAge 策略 |
| SQLite | `data/FitnessDatabase.kt` | schema migration |
| Store | `data/FitnessStore.kt` | 新字段读写与日历查询 |
| Repository | `data/FitnessRepository.kt` | 校验、AI Prompt 全字段、日历聚合、头像存储协调 |
| 备份 | `data/FitnessBackupCodec.kt` | BMI/头像备份，旧 bodyAge 兼容 |
| AI Client | `ai/AiChatClient.kt` | Provider catalog 和请求适配 |

## 15. 当前实现差距

开发开始前应确认以下已知差距：

1. 当前原生 UI 仍包含“身体年龄”输入和校验，需要移除。
2. 当前 `BodyMeasurement` 没有 BMI 持久化字段，需要新增迁移。
3. 当前 AI 计划 Prompt 只包含部分档案/体测，需要扩展到本文 15 项。
4. 当前数据库存在 legacy `body_age`，需要只读兼容并停止写入。
5. 当前 AI provider 仍以旧默认配置为主，需要替换为 OpenAI/Gemini/Qwen 目录。
6. 当前原生档案没有头像路径和 Photo Picker 流程。
7. 当前计划页需要补齐周/月/年真实聚合及日期详情查询。

## 16. 测试要求

### 16.1 JVM 单元测试

- 15 项 AI Prompt 字段全部存在；头像和 bodyAge 不存在。
- 空可选体测字段输出“未填写”，不使用假默认值。
- 周/月/年日历状态聚合正确。
- 闰年、28/29/30/31 天和每月星期偏移正确。
- Provider 切换后 endpoint/model 目录正确。
- 头像裁剪尺寸、文件类型、大小限制和替换清理逻辑正确。

### 16.2 Instrumented / Repository 测试

- 数据库旧版本迁移后历史档案和训练记录不丢失。
- BMI、头像路径和全部体测可保存、读取、备份、恢复。
- 旧备份含 `bodyAge` 时可导入且忽略该值。
- 历史训练和未来计划能同时出现在日历聚合中。
- API Key 不出现在 SQLite、日志和备份中。

### 16.3 Compose UI 测试

- 五项中文导航顺序固定。
- 年视图点击任意月份进入正确月视图。
- 月视图点击训练日、计划日和无安排日分别展示正确详情。
- 全部档案字段可编辑，身体年龄不可见。
- 头像上传后立即预览，重启 App 后仍显示。
- AI 草稿显示 15 项输入快照，修改偏好后草稿同步变化。
- 服务商 Logo 可见，endpoint/model 为下拉选择。
- 倒计时数字与进度条不重叠。

### 16.4 真实设备验收

在 `Pixel_8_Pro / emulator-5554` 或等效设备完成：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

必须提供：

- 首页、周/月/年日历、日期详情、训练休息、训练总结、档案编辑、头像、AI 设置、AI 草稿截图。
- 无崩溃、ANR、资源 404 或横向溢出。
- SQLite/Repository 证据证明数据真实保存，而非仅 Compose 假状态。
- AI Prompt 测试证据证明 15 项字段覆盖和头像/bodyAge 排除。

## 17. 验收清单

- [ ] 所有 22 个页面/状态均有原生实现或明确路由。
- [ ] 黑白荧光黄未来主义新拟态视觉统一。
- [ ] 首页图片与按钮水平对齐。
- [ ] 无圆环倒计时/总结重叠问题。
- [ ] 周、月、年日历都使用真实数据。
- [ ] 日期可点击查看训练详情。
- [ ] 年视图月份可跳转到对应月视图。
- [ ] 15 项档案全部可编辑、持久化、备份并进入 AI Prompt。
- [ ] 身体年龄不出现在 UI、AI Prompt 或新备份中。
- [ ] 头像可上传、更换、恢复，且不发送给 AI。
- [ ] OpenAI、Gemini、千问服务商与 Logo 可用。
- [ ] 接口地址和模型均为服务商联动下拉框。
- [ ] AI 计划先生成草稿，确认后才写入正式计划。
- [ ] API Key 使用 Keystore，不进入 SQLite/日志/备份。
- [ ] JVM、设备测试、APK 构建、安装和模拟器验收全部通过。

## 18. 推荐实施顺序

1. 数据库迁移：BMI、头像路径、legacy bodyAge 兼容。
2. Entity/Store/Backup/Repository 全链路与测试。
3. 档案编辑、头像 Photo Picker 和“我的”摘要。
4. AI Prompt 15 项字段与草稿输入快照。
5. AI Provider catalog、Logo、地址/模型下拉和 Keystore 测试。
6. 日历周/月/年聚合、月份跳转和日期详情。
7. 首页与训练执行视觉迁移，移除圆环问题。
8. 全页面视觉统一、无障碍与响应式检查。
9. Gradle、APK、模拟器、SQLite 和 Prompt 闭环验收。

## 19. 变更历史

| 版本 | 日期 | 内容 |
| :--- | :--- | :--- |
| 1.0.0 | 2026-07-11 | 汇总未来主义新拟态原型、训练日历、可编辑体测、AI 全字段、三服务商和头像上传需求。 |
