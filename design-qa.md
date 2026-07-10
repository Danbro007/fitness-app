# Android layout migration design QA

## Review setup

- Source visual: `.scratch/run-evidence/redesign-interactive-mobile-430.png`
- Implementation visual: `.scratch/run-evidence/android-layout-migration/home-pre-workout.png`
- Side-by-side comparison: `.scratch/run-evidence/android-layout-migration/design-qa-comparison.png`
- Implementation viewport: Pixel 8 Pro AVD, 1344 × 2992, light theme
- Matched state: seeded local plan, workout not started, `0 / 2`, primary action `开始训练`

## Visual comparison

- Preserved the source hierarchy: venue context, oversized daily title, dark workout hero, weekly rhythm, secondary quick actions, and persistent bottom navigation.
- Preserved the warm neutral background, near-black hero, rounded white surfaces, green status color, large Chinese display type, and local exercise artwork.
- The native implementation uses one clear orange primary action inside the hero. This follows the approved Android product direction and keeps the workout action visually distinct from green status feedback.
- The native implementation intentionally replaces the reference prototype's `今天 / 日历 / 场地 / 动作 / AI` navigation with the approved product information architecture: `首页 / 计划 / 训练 / 饮食 / 我的`.
- The source's in-progress exercise list is represented by `训练` and `动作库` routes in the native app, keeping the home screen focused on one primary task and two secondary shortcuts.

## Severity review

- P0: none. Core navigation and workout start path are usable.
- P1: none. No clipped primary controls, broken navigation, unreadable text, or missing required state.
- P2: none. Card spacing, radii, contrast, imagery, hierarchy, and bottom-navigation safe-area handling are consistent across the captured screens.
- P3: none requiring a code change. The remaining source differences are intentional product-state and information-architecture decisions documented above.

## Supporting screens reviewed

- `home.png`: completed-workout state, weekly progress `1 / 2`
- `plan.png`: weekly schedule and four-week plan draft
- `training-prep.png`: exercise preparation and start action
- `training-active.png`: immersive active workout without global bottom navigation
- `workout-summary.png`: persisted set, volume, duration, and feedback summary
- `food.png`: nutrition totals and persisted meal timeline
- `profile.png`: local profile, workout totals, and settings hierarchy

final result: passed
