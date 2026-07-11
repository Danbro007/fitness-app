# Futuristic Neumorphism Design QA

- Visual source of truth: `.scratch/futuristic-neumorphism-prototype/i-fitness-未来主义新拟态交互原型.html`
- Requirements source: `docs/superpowers/specs/2026-07-11-futuristic-neumorphism-ui-development-requirements.md`
- Comparison viewport: 390 × 844 CSS-equivalent pixels (Pixel 8 Pro emulator at 1170 × 2532, density 480, screenshots normalized to 390 × 844)
- Implementation captures: `.scratch/futuristic-neumorphism-android/qa/`
- Reference captures: `output/playwright/futuristic-*-390*.png`

## Final comparison

| State | Reference | Implementation | Result |
| --- | --- | --- | --- |
| Home | `futuristic-home-390.png` | `home.png` | Passed |
| Plan / week | `futuristic-plan-week-390.png` | `plan-week.png` | Passed |
| Plan / month | `futuristic-plan-month-390.png` | `plan-month.png` | Passed |
| Plan / year | `futuristic-plan-year-390.png` | `plan-year.png` plus fresh emulator recapture | Passed |
| Food | prototype Food state | `food.png` | Passed |
| Profile | prototype Profile state | `profile.png` | Passed |
| Training active / rest / summary | `futuristic-training-active-390.png`, `futuristic-training-rest-390.png`, `futuristic-summary-390.png` | native screens exercised by connected UI tests | Passed |
| Profile edit / AI settings | `futuristic-profile-edit-390.png`, `futuristic-smart-390.png`, provider references | native screens exercised by connected UI tests | Passed |

## Findings and correction history

1. Replaced the previous generic Material palette with the prototype tokens: warm `#F4F4EF` page, near-black `#10110F`, fluorescent `#EFFF31`, 22–34 dp radii, soft shadow cards, and the 82 dp floating navigation dock.
2. Rebuilt the five primary destinations around the reference hierarchy instead of reskinning the old layout: oversized home hero, calendar spotlight and segmented views, food macro bento, profile avatar/stat card, and selected navigation tiles.
3. Added the missing reference states and product behavior: week/month/year calendar persistence, provider cards and dropdowns, profile avatar handling, structured AI profile snapshot, full-screen rest timer, and workout summary.
4. Corrected scroll and semantics regressions found by instrumentation tests after the visual restructuring.
5. A first year-view screenshot contained a transient black capture region. The state was reopened and recaptured on the emulator; the live screen showed the expected fixed 240 dp spotlight and year grid, so no product defect remained.

## Verification

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest`
- Result: `BUILD SUCCESSFUL`; 70 connected tests completed, 0 failed.
- `git diff --check`: clean.
- Severity review: no remaining P0, P1, or P2 visual findings in the checked states.

final result: passed
