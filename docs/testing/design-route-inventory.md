# HTML 设计稿与 Android 路由对照

视觉真相：`.scratch/futuristic-neumorphism-prototype/i-fitness-未来主义新拟态交互原型.html`

| HTML `data-screen` | Android 实现 | 当前结论 |
| --- | --- | --- |
| `home` | `Primary(Home)` / `HomeScreen` | 已完成 390×844 成对 QA |
| `plan` | `Primary(Plan)` / `PlanScreen` | 已完成 390×844 成对 QA |
| `training` | `Primary(Training)` / `TrainingPreparationScreen` | 已完成 390×844 成对 QA |
| `training-active` | `TrainingActive` / `TrainingActiveScreen` | 已完成 390×844 成对 QA |
| `training-rest` | `TrainingActiveScreen` 内部状态 | 已完成 390×844 成对 QA |
| `food` | `Primary(Food)` / `FoodScreen` | 已完成 390×844 成对 QA |
| `profile` | `Primary(Profile)` / `ProfileScreen` | 已完成 390×844 成对 QA |
| `library` | `Library` / `LibraryScreen` | 已完成 390×844 成对 QA |
| `exercise-detail` | `ExerciseDetail` / `ExerciseDetailScreen` | 已完成 390×844 成对 QA |
| `plan-detail` | `PlanDetail` / `PlanDetailScreen` | 已完成 390×844 成对 QA |
| `plan-edit` | `PlanEdit` / `PlanEditScreen` | 已完成 390×844 成对 QA |
| `plan-draft` | `PlanDraft` / `PlanDraftScreen` | 已完成 390×844 成对 QA |
| `onboarding` | `ProfileEditScreen(isInitialSetup = true)` | 已完成 390×844 成对 QA |
| `profile-edit` | `ProfileEdit` / `ProfileEditScreen` | 已完成 390×844 成对 QA |
| `venue` | `VenueSettings` / `VenueSettingsScreen` | 已完成 390×844 成对 QA |
| `smart` | `SmartSettings` / `SmartSettingsScreen` | 已完成 390×844 成对 QA |
| `backup` | `DataBackup` / `BackupSettingsScreen` | 已完成 390×844 成对 QA |
| `about` | `About` / `AboutScreen` | 已完成 390×844 成对 QA |
| `food-manual` | `FoodManual` / `FoodManualScreen` | 已完成 390×844 成对 QA |
| `food-photo` | `FoodPhoto` / `FoodPhotoScreen` | 已完成 390×844 成对 QA |
| `food-photo-draft` | `FoodPhotoDraft` / `FoodPhotoDraftScreen` | 已完成 390×844 成对 QA |
| `summary` | `WorkoutSummary` / `WorkoutSummaryScreen` | 已完成 390×844 成对 QA |

## HTML 弹层状态

| HTML 弹层 | Android 实现 | 当前结论 |
| --- | --- | --- |
| 视觉模式 | `ProfileScreen` / `ModalBottomSheet` | 已完成 390×844 成对 QA |
| 结束训练确认 | `TrainingActiveScreen` 底部弹层 | 已完成 390×844 成对 QA |
| 添加一餐 | `FoodScreen` / `ModalBottomSheet` | 已完成 390×844 成对 QA |
| 日历当日详情 | `PlanScreen` 当日详情弹层 | 已完成 390×844 成对 QA |

## 通过门槛

每个表格项都必须拥有同一视口、同一内容、同一交互状态的 HTML 源图与 Android 截图。仅编译、自动化功能测试或代码结构一致不能将项目标记为完成。
