# i fitness 全量验收测试用例

- 日期：2026-07-14
- 关联计划：`docs/testing/2026-07-14-full-acceptance-test-plan.md`
- 状态值：`未执行 / 通过 / 失败 / 阻塞 / 不适用`
- 优先级：`P0 / P1 / P2 / P3`

## 1. 首次设置、档案与头像

| ID | 优先级 | 场景与步骤 | 预期结果 | 自动化接缝 |
| --- | --- | --- | --- | --- |
| ONB-001 | P0 | 清空数据后冷启动 | 只显示首次设置，不闪现首页，不崩溃 | Compose + APK |
| ONB-002 | P1 | 空表单、越界出生年/身高/体重提交 | 不写库，逐项可理解提示，已填值保留 | Compose + Repository |
| ONB-003 | P0 | 填写昵称、1994、176、76.5、目标、3 天、45 分钟并完成 | 进入首页；重启不再出现引导；SQLite 回显一致 | Compose + SQLite + APK |
| ONB-004 | P1 | 输入中文昵称、中文伤病说明并保存 | 中文输入、回显、搜索和重建均正常 | Compose + APK |
| PRO-001 | P1 | 编辑 15 项档案/体测后保存并重启 | 全部字段持久化且摘要正确；身体年龄不可见 | Repository + Compose |
| PRO-002 | P1 | 为每个数字字段输入空、非数字、边界内外值 | 合法值通过；非法值不崩溃、不落库 | JVM + Compose |
| AVA-001 | P1 | 选择 JPG/PNG/WebP 横图、竖图和大图 | 安全读尺寸、居中裁正方形、压缩并回显 | Repository + Compose |
| AVA-002 | P0 | 选择伪装格式、损坏图片、超尺寸图片 | 明确失败，不 OOM，不破坏旧头像 | Repository + APK |
| AVA-003 | P1 | 更换头像、备份、重置、恢复 | 新头像显示；旧文件清理；恢复后显示 | Repository + SQLite |
| AVA-004 | P0 | 检查 AI Prompt、日志和备份内容 | 头像不发送 AI、不进日志；只进入本地备份 | JVM + Repository |

## 2. 首页与导航

| ID | 优先级 | 场景与步骤 | 预期结果 | 自动化接缝 |
| --- | --- | --- | --- | --- |
| NAV-001 | P0 | 检查底栏并循环点击两轮 | 仅首页/计划/训练/饮食/我的，顺序和选中态正确 | JVM + Compose |
| HOME-001 | P1 | 无计划、待训练、进行中、已完成四种状态打开首页 | 始终只有一个正确主 CTA，标题/进度/图片来自同一任务 | JVM + Compose |
| HOME-002 | P1 | 快速入口动作库→返回→饮食→首页→动作库 | 每次单次导航，无透明遮罩，无 ANR | Compose + APK |
| HOME-003 | P1 | 快速双击主 CTA 和快捷入口 | 不重复建页、不重复创建训练 session | Compose + Repository |
| NAV-002 | P1 | 从不同一级页进入动作详情/设置后页面返回和系统返回 | 返回真实来源；底栏显示状态正确 | JVM + Compose |
| NAV-003 | P1 | 在一级和二级路由重建 Activity/杀进程恢复 | 合法路由恢复；失效 session 提供返回首页 | Compose + APK |

## 3. 计划和训练日历

| ID | 优先级 | 场景与步骤 | 预期结果 | 自动化接缝 |
| --- | --- | --- | --- | --- |
| PLAN-001 | P0 | 创建明日计划并添加两个动作 | 进入真实详情；SQLite 写入一次；重启存在 | Repository + Compose |
| PLAN-002 | P1 | 同日创建两条计划并分别打开 | 两条均显示且详情不串数据 | Repository + Compose |
| PLAN-003 | P1 | 编辑名称/日期/组数/次数/重量并保存 | UI 和 SQLite 同步；非法值不落库 | Repository + Compose |
| PLAN-004 | P1 | 删除、跳过、复制计划并确认/取消 | 每个动作只影响目标计划，取消无写入 | Repository + Compose |
| CAL-001 | P0 | 周/月/年切换并重启 | 维度、月份和聚合正确；最后维度持久化 | Repository + Compose |
| CAL-002 | P1 | 年视图点月份 | 切月视图并定位正确月份、天数和星期起始 | Compose |
| CAL-003 | P0 | 点击历史完成日、未来计划日、空日期 | 显示真实 session/log、真实计划或休息日，不伪造 | Repository + Compose |
| CAL-004 | P1 | 跨月、闰年、月首月末、同日多计划 | 日期格与详情无越界、无漏项 | JVM + Repository |
| AIP-001 | P0 | 无 Key 主动生成四周计划 | 生成明确本地兜底草稿，不直接写正式计划 | Repository + Compose |
| AIP-002 | P0 | 生成 AI 草稿后返回、确认、重复确认 | 未确认零正式写入；确认一次生成；重复确认被拒绝 | Repository + SQLite |
| AIP-003 | P0 | 检查计划 Prompt 输入 | 15 项档案、场地、器械、历史齐全；无头像/bodyAge/Key | JVM + Repository |
| AIP-004 | P1 | AI 返回 Markdown、空内容、畸形内容 | 页面不裸露 Markdown 符号；失败可重试且不污染计划 | JVM + Compose |

## 4. 动作库、器械与 GIF

| ID | 优先级 | 场景与步骤 | 预期结果 | 自动化接缝 |
| --- | --- | --- | --- | --- |
| LIB-001 | P0 | 打开 1,324 动作列表并连续滚动 | Lazy 渲染，无主线程全量 GIF 解码，无 ANR | Compose + APK |
| LIB-002 | P1 | 中文搜索名称、部位、器械；清空后再搜 | 中文输入正常，结果准确，可持续输入 | JVM + Compose + APK |
| LIB-003 | P1 | 组合部位/器械/类型筛选并清除 | 条件交集正确，隐藏选中项仍保存 | Repository + Compose |
| EQP-001 | P1 | 搜索门店器械名称和别名并按类型查看 | 有氧/复合无氧/固定/自由器械分类可查 | Repository + Compose |
| EQP-002 | P1 | 多选器械、返回、重启 | 选中态易读、不显示多余首字母、SQLite 持久化 | Compose + SQLite |
| GIF-001 | P0 | 默认构建打开动作详情 | 授权门禁有效；无媒体时提供清晰占位 | Gradle + Compose |
| GIF-002 | P0 | 媒体构建打开多个横竖比例 GIF | 完整画面 `Fit` 显示、可离线播放、不裁切、不显示调试文案 | Compose + APK |
| GIF-003 | P1 | 列表快速滚动、详情反复进出、后台恢复 | 无泄漏、OOM、ANR，非可见 GIF 不继续批量解码 | APK + logcat |
| LIB-004 | P1 | 从计划/训练/首页三种来源添加动作 | 加入正确目标，返回正确来源，重启后存在 | Repository + Compose |

## 5. 训练执行、总结与调整

| ID | 优先级 | 场景与步骤 | 预期结果 | 自动化接缝 |
| --- | --- | --- | --- | --- |
| TRN-001 | P0 | 从计划开始训练 | 只创建一条进行中 session，隐藏全局底栏 | Repository + Compose |
| TRN-002 | P0 | 直接编辑重量/次数并选轻松/合适/吃力后保存 | 实际值和体感写入对应 set log | Compose + SQLite |
| TRN-003 | P0 | 快速双击完成本组 | 只写一组；按钮保存中禁用；无重复 set index | Repository + Compose |
| TRN-004 | P1 | 暂停、继续、休息倒计时、延长 30 秒、跳过 | 计时和运行态持久化，动作结束后进入下一个未完成动作 | Repository + Compose |
| TRN-005 | P0 | 训练中 Activity 重建、切后台、杀进程再开 | 已完成组、当前动作、休息/暂停状态恢复，不新建 session | Repository + APK |
| TRN-006 | P1 | 中途结束先取消再确认 | 取消继续训练；确认只保存已完成组且不计整次完成 | Repository + Compose |
| TRN-007 | P0 | 完成最后一组 | 显示 7/7 和“全部训练组已完成”；隐藏录入控件；总结 CTA 可用 | Compose |
| TRN-008 | P0 | 完整结束进入总结 | 组数、目标、容量、时长、每动作明细、体感均来自真实日志 | Repository + Compose |
| REV-001 | P0 | 填训练后感受/备注并生成复盘 | 输入含实际组数、重量、次数、组间体感、完成度和恢复反馈 | JVM + Repository |
| REV-002 | P0 | 无 AI Key 生成复盘 | 本地规则总结可用，不阻塞训练保存 | Repository + Compose |
| REV-003 | P0 | 得到加量/保持/减量建议后先放弃再确认 | 草稿不改计划；仅确认后改未来同动作计划 | Repository + SQLite |
| REV-004 | P1 | 极低完成度、过度疲劳、完整轻松三组输入 | 分别减量、保持或加量，边界行为可解释 | JVM |

## 6. 饮食与照片估算

| ID | 优先级 | 场景与步骤 | 预期结果 | 自动化接缝 |
| --- | --- | --- | --- | --- |
| FOOD-001 | P1 | 手动记录空/负数/非数字/边界值 | 非法输入有提示且不落库，合法输入汇总正确 | Compose + Repository |
| FOOD-002 | P1 | 保存一餐后重启 | 今日记录、热量和三大营养素不丢失、不重复 | Repository + APK |
| FOOD-003 | P0 | 选择食物图片 | 选择后立即回显，旋转/重建仍可见 | Compose + APK |
| FOOD-004 | P0 | 无 Key、无网、无效 Key 估算 | 明确本地兜底或错误，可重试，不无限加载 | Repository + Compose |
| FOOD-005 | P0 | AI 返回 Markdown 估算 | 结构可读，不显示 `#`、`**`、列表语法等原始符号 | JVM + Compose |
| FOOD-006 | P0 | 生成草稿后返回、确认、重复确认、放弃 | 未确认不写 food log；确认一次；放弃无正式数据 | Repository + SQLite |
| FOOD-007 | P0 | 损坏/超大/伪装图片 | 安全校验，不 OOM、ANR 或崩溃，不丢已有饮食 | Repository + APK |

## 7. AI 设置、安全、备份和重置

| ID | 优先级 | 场景与步骤 | 预期结果 | 自动化接缝 |
| --- | --- | --- | --- | --- |
| AI-001 | P1 | 切换 OpenAI/Gemini/千问 | Logo、endpoint、model 目录联动；浏览未保存不改当前服务商 | JVM + Compose |
| AI-002 | P0 | 保存、读取、删除 API Key | Android Keystore + AES/GCM 可用；SQLite 无明文 | Device + SQLite |
| AI-003 | P0 | 检查 logcat、备份、截图和异常消息 | 不包含 API Key 或完整敏感 Prompt | Device + APK |
| AI-004 | P1 | 无效 Key/超时/断网测试连接 | 有明确中文失败反馈，主线程不阻塞 | Compose + APK |
| BAK-001 | P0 | 完整数据导出、重置、导入 | 档案/头像/计划/训练/饮食/草稿/偏好往返一致 | Repository + SQLite |
| BAK-002 | P0 | 导入 V1/V2/含 bodyAge 的旧备份 | 向后可读，忽略 bodyAge，其他数据不丢 | Repository |
| BAK-003 | P0 | 导入 JSON 损坏、头像损坏、事务中注入写失败 | 原数据不变；损坏头像可跳过；无半恢复状态 | Repository |
| BAK-004 | P1 | 系统文件选择器取消导入/导出 | 回到 App 可继续使用，不显示虚假成功 | APK |
| RST-001 | P0 | 重置先取消再确认 | 取消无变化；确认清业务数据和凭证并回首次设置 | Repository + Compose |

## 8. 稳定性、性能、无障碍与兼容

| ID | 优先级 | 场景与步骤 | 预期结果 | 自动化接缝 |
| --- | --- | --- | --- | --- |
| STB-001 | P0 | 全流程运行后扫描 logcat | 无 FATAL EXCEPTION、ANR、未捕获异常 | APK |
| STB-002 | P0 | 首页快捷入口、列表筛选、完成本组各连点 20 次 | 无卡死、重复写、重复路由 | Compose + APK |
| STB-003 | P1 | 冷启动、首页、动作库首屏和训练交互计时 | 无主线程数据库/网络/全库索引；交互无明显掉帧 | APK + profiler/logcat |
| STB-004 | P1 | API 26 启动、GIF、数据库迁移和核心训练 | 不使用仅新 API 的无保护实现 | Device |
| A11Y-001 | P1 | 扫描 icon-only 控件语义和 48 dp 点击区 | 均有中文描述，可被测试定位，触控面积合格 | Compose |
| A11Y-002 | P1 | 检查浅/深色页面、禁用态、底部弹层文字对比度 | 文字清晰，不以颜色作为唯一状态 | JVM + screenshot |
| UI-001 | P1 | 390 × 844 dp 对照 22 页面/状态 | 排版、层级、圆角、间距、底栏与设计基线一致 | Screenshot |
| UI-002 | P1 | 中文长文本、系统字体 1.3×、键盘弹出、滚到底部 | 不截断关键操作，不被键盘/底栏永久遮挡 | APK |

## 9. 构建、覆盖率与交付门禁

| ID | 优先级 | 场景与步骤 | 预期结果 |
| --- | --- | --- | --- |
| GATE-001 | P0 | 运行 JVM、Lint、Debug APK、AndroidTest APK | 全部成功 |
| GATE-002 | P0 | 运行全部 connected Android tests | 除显式凭证联调外全部成功；联调用例默认安全跳过 |
| GATE-003 | P0 | 生成并合并 JVM/设备 JaCoCo 报告 | 行/分支/方法/类均为 100%，门禁任务成功 |
| GATE-004 | P0 | 默认构建检查 GIF，媒体构建检查许可证参数 | 默认不分发第三方二进制；媒体门禁不可绕过 |
| GATE-005 | P1 | `git diff --check` 与 GitNexus detect changes | 无格式错误；意外影响流程为零或已解释回归 |
| GATE-006 | P0 | 覆盖安装 APK、冷启动并复跑 P0 真实点击链路 | 安装成功、进程存活、数据符合预期、无 ANR/崩溃 |

## 10. 需求追踪摘要

| 需求域 | 对应用例 |
| --- | --- |
| 五项一级导航与 22 页面 | NAV-*、UI-* |
| 周/月/年日历与日期详情 | CAL-*、PLAN-* |
| 训练执行、总结与 AI 调整 | TRN-*、REV-* |
| 15 项档案、头像与隐私 | PRO-*、AVA-*、AIP-003 |
| 三 AI 服务商与 Keystore | AI-* |
| 饮食和图片估算草稿 | FOOD-* |
| 本地优先、备份与旧版本 | BAK-*、RST-* |
| GIF 授权与完整显示 | GIF-*、GATE-004 |
| 100% 覆盖率和真实 APK | GATE-* |
