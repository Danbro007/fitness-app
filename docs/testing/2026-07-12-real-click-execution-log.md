# i Fitness 真实点击执行记录

- 执行环境：Pixel 8 Pro AVD，API 36，无窗口 SwiftShader 软件渲染
- APK：当前工作区 `app-debug.apk`
- 操作方式：ADB 触摸、滑动、键盘与系统返回事件；每次页面切换后等待界面稳定
- 证据目录：`.scratch/click-test-2026-07-12/`

## 环境校验

Host GPU 模式曾出现 App 与 Android 系统桌面同时黑屏、画面撕裂，确认是模拟器 Vulkan/Host GPU 故障，不能记为 App Bug。后续全部结论来自 SwiftShader 软件渲染环境。

## 已执行

| 用例 | 结果 | 关键证据 |
| --- | --- | --- |
| CLICK-ONBOARD-001 | 通过 | 干净启动显示首次训练设置；bootstrap 日志为 1324 条动作、0 失败 |
| CLICK-ONBOARD-002 | 通过 | 空表单提交显示“请输入合理的出生年份”，未进入首页 |
| CLICK-ONBOARD-004 | 通过 | 屏幕点击输入档案后进入首页；五个一级导航可见 |
| CLICK-HOME-002 | 通过 | 首页点击记录饮食后进入饮食页，营养汇总正常显示 |
| CLICK-HOME-003 | 通过 | 首页进入动作库，返回后再点记录饮食成功 |
| CLICK-HOME-004 | 通过 | 动作详情/动作库返回链路可回到首页，后续点击仍响应 |
| CLICK-HOME-005 | 通过 | 真实点击五个一级导航两轮，最终正确回到首页；logcat 无 FATAL/ANR |
| CLICK-LIB-001 | 通过 | 全部 1324 条；背筛选 203 条；随后腿筛选 286 条 |
| CLICK-LIB-003 | 通过 | 首页来源可打开动作详情；无计划时明确显示“请先选择一个训练计划” |
| CLICK-FOOD-001 | 通过 | 手动记录空提交显示名称、热量、蛋白质、碳水校验，未写入 SQLite |
| CLICK-FOOD-002 | 通过 | 点击并键盘输入 ChickenRice、600 kcal、40/70/15g；UI 汇总、SQLite 与重启后数据一致 |
| CLICK-FOOD-003 | 通过 | 未保存 API Key 时仍明确生成本地估算草稿；正式总计保持 600 kcal |
| CLICK-FOOD-004 | 通过 | 确认草稿前 SQLite 为 1 条/600 kcal；点击确认后为 2 条/1120 kcal |
| CLICK-PLAN-001 | 通过 | 点击新增并确认默认次日计划后立即进入“自定义训练”详情；2 个动作 |
| CLICK-PLAN-002 | 失败后已修复 | 实际点击创建 PlanA/PlanB；修复后显示“已计划 2”、“2 个计划 · PlanA、PlanB”，点击 7 月 13 日后面板可分别打开两条 |
| CLICK-PLAN-003 | 失败后已修复 | `2026-99-99` 原先显示 Java 英文解析异常；现显示“请输入有效日期（格式 YYYY-MM-DD）”且表单可滚动 |
| CLICK-WORKOUT-001 | 通过 | 从训练 Tab 开始刚创建的计划，进入沉浸训练页，底栏隐藏 |
| CLICK-WORKOUT-002 | 通过 | 快速双击完成本组后 SQLite 仅一条 `set_index=1` 记录 |
| CLICK-WORKOUT-003 | 通过 | 进入休息倒计时并可点击跳过 |
| CLICK-WORKOUT-004 | 通过 | 强制停止并重启后恢复到训练页，显示史密斯机卧推 1/4 |
| CLICK-WORKOUT-005 | 通过 | 首次点击结束后取消仍停留训练；再次确认进入部分完成总结，1/7 组且周次数不虚增 |
| CLICK-PROFILE-001 | 通过 | 点击编辑档案，将昵称改为 ClickTester2；保存后立即显示新昵称 |
| CLICK-PROFILE-002 | 通过 | 连续打开龙门架和固定轨迹器械开关，两个 checked 状态均持久更新 |
| CLICK-PROFILE-003 | 通过 | 点击 OpenAI/Gemini/千问，endpoint 与模型随服务商联动更新 |
| CLICK-PROFILE-004 | 通过 | 点击导出进入系统 DocumentsUI，系统返回取消后回到数据备份页且可继续操作 |
| CLICK-PROFILE-006 | 通过 | 取消路径通过；最终真实点击确认后，档案 1→0、计划 2→0，并自动回到首次设置页 |

## SQLite 证据

快速双击“完成本组”后的实际记录：

```text
session_id=35da1e3a-6dfb-473b-a211-2293393d4502
exercise_id=0748
set_index=1
actual_reps=8
actual_weight_kg=0.0
completed=1
```

没有生成第二条重复组记录。

手动饮食有效提交后的 SQLite 记录：

```text
name=ChickenRice
calories=600
protein_grams=40.0
carbs_grams=70.0
fat_grams=15.0
```

照片描述 `noodles` 的确认边界：

```text
确认前: food_log=1, calories_sum=600
确认后: food_log=2, calories_sum=1120
```

服务商联动点击结果：

```text
Gemini endpoint=https://generativelanguage.googleapis.com/v1beta/openai
Gemini model=gemini-3.5-flash
Qwen endpoint=https://dashscope.aliyuncs.com/compatible-mode/v1
Qwen model=qwen3.7-plus
```

## 当前发现

1. 当前构建未复现“进入动作库后其他按钮永久失效”；在稳定环境中筛选和返回后的饮食入口均正常。
2. 页面切换在软件模拟器上需要等待；连续发送下一次坐标前若页面尚未稳定，会点击到新页面同一坐标的其他控件。执行记录只采用等待稳定后的结果。
3. 尚未完成全部 40 条用例，目标保持进行中。

## 本轮点击发现并修复

### BUG-CLICK-PLAN-001 非法日期泄漏英文框架异常

- 复现：计划 → 新增 → 日期输入 `2026-99-99` → 确认创建。
- 修复前：显示 `Text '2026-99-99' could not be parsed...`。
- 修复后：显示“请输入有效日期（格式 YYYY-MM-DD）”。
- 额外修复：新增计划 Bottom Sheet 支持滚动，保证小窗口或错误提示出现后按钮和提示仍可达。
- 回归：真实点击通过；设备集成测试通过。

### BUG-CLICK-PLAN-002 周日创建次日计划后无法重新访问

- 复现：周日分别创建次日同日的 PlanA、PlanB → 返回计划页。
- 数据证据：SQLite 中 `PlanA|2026-07-13`、`PlanB|2026-07-13` 两条均存在。
- 修复前：标题写“7 月 6—14 日”，实际只渲染 6—12 日；摘要按日期去重显示“已计划 1”，两条计划均无法重新打开。
- 修复：周视图按标题覆盖 9 天；已计划数量按计划条数计算；同日多计划显示数量和名称，点击日期详情展示全部计划。
- 回归：两条设备集成测试、Lint 和 APK 构建通过；真实点击最终回归也已通过，证据为 `82-day13-multi-plan.xml/png`。

## 最终破坏性验证

点击“确认重置”前后的 SQLite 计数：

```text
重置前: profile=1, plans=2, food=0, sessions=0, drafts=0
重置后: profile=0, plans=0, food=0, sessions=0, drafts=0
```

重置后界面立即显示“先完成训练设置”，无残留个人数据。
