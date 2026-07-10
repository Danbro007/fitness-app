# i fitness Interactive Layout Prototype Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a browser-based, mobile-width interactive prototype that validates the five-tab information architecture and the complete local workout-recording flow before any Compose code changes.

**Architecture:** Create a new standalone HTML prototype beside the existing design explorations, reusing the current GIF assets and interaction patterns without modifying the historical prototype. A single JavaScript state object drives hash routing, localStorage persistence, page rendering, and the workout state machine; a Playwright test file exercises the rendered UI through a local HTTP server.

**Tech Stack:** Semantic HTML, CSS, vanilla JavaScript, browser `localStorage`, Node.js 25 `node:test`, Playwright 1.61.1, Python `http.server`.

## Global Constraints

- Do not modify Kotlin, Compose, SQLite, Gradle, or Android navigation in this plan.
- The prototype viewport target is exactly `390×844`; it must also render without horizontal overflow at `430px` width.
- Bottom navigation has exactly five destinations in this order: `首页`, `计划`, `训练`, `饮食`, `我的`.
- The home screen exposes exactly one primary workout action for the current state.
- Active workout mode hides global bottom navigation and keeps `完成本组` visible in the `390×844` viewport.
- Use the existing cream background, orange primary action, dark workout surface, green progress state, and local exercise GIF assets.
- Orange is reserved for primary actions; green is reserved for progress, success, and selection.
- All interactive controls have a minimum hit area of `48×48px`; normal body text contrast is at least `4.5:1`.
- Persist prototype state under `ifitness-layout-prototype-v1`; AI actions use local demo drafts only.
- Do not add account, cloud sync, social, real AI calls, or API-key storage.
- Support `prefers-reduced-motion` and do not rely on animation to communicate state.

## File Structure

- Create: `.scratch/fitness-app-mvp/健身App-单一主任务交互原型.html` — complete responsive prototype, styles, render functions, state, routing, and event handling.
- Create: `.scratch/fitness-app-mvp/健身App-单一主任务交互原型.test.cjs` — Playwright flow, persistence, responsiveness, and accessibility-contract tests.
- Create during verification: `.scratch/run-evidence/interactive-layout-prototype/*.png` — accepted screenshots for home, plan, training, food, profile, and workout summary.
- Reference only: `.scratch/fitness-app-mvp/健身App-主流移动端交互重设计.html` — source interaction patterns and GIF paths; do not edit.
- Reference only: `app/src/main/assets/exercise-media/gifs/0748-trqKQv2.gif` — Smith machine bench press.
- Reference only: `app/src/main/assets/exercise-media/gifs/0289-SpYC0Kp.gif` — dumbbell bench press.
- Reference only: `docs/superpowers/specs/2026-07-10-interactive-layout-prototype-design.md` — approved product and interaction specification.

## Test Command

Use the bundled Playwright package without modifying the Android repository dependencies:

```bash
PYTHON_BIN="$(command -v python3)"
NODE_BIN="/Users/shanqijie/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node"
NODE_PATH="/Users/shanqijie/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules"
"$PYTHON_BIN" -m http.server 8087 --directory . > /tmp/ifitness-prototype-http.log 2>&1 &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true' EXIT
NODE_PATH="$NODE_PATH" "$NODE_BIN" --test .scratch/fitness-app-mvp/健身App-单一主任务交互原型.test.cjs
```

Expected final output: all tests pass with `fail 0`.

---

### Task 1: Prototype shell, persisted state, five-tab navigation, and focused home

**Files:**
- Create: `.scratch/fitness-app-mvp/健身App-单一主任务交互原型.html`
- Create: `.scratch/fitness-app-mvp/健身App-单一主任务交互原型.test.cjs`

**Interfaces:**
- Consumes: GIF paths and interaction patterns from `.scratch/fitness-app-mvp/健身App-主流移动端交互重设计.html`.
- Produces: `STORAGE_KEY`, `DEFAULT_STATE`, `loadState()`, `saveState()`, `navigate(route)`, `render()`, `renderHome()`, `renderBottomNav()`, and stable `data-testid` selectors used by later tasks.

- [ ] **Step 1: Write the failing navigation and home contract test**

Create the test harness and first test:

```js
const { test, before, beforeEach, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { chromium } = require('playwright');

const prototypeUrl = 'http://127.0.0.1:8087/.scratch/fitness-app-mvp/%E5%81%A5%E8%BA%ABApp-%E5%8D%95%E4%B8%80%E4%B8%BB%E4%BB%BB%E5%8A%A1%E4%BA%A4%E4%BA%92%E5%8E%9F%E5%9E%8B.html';
let browser;
let page;

before(async () => {
  browser = await chromium.launch({ headless: true });
  page = await browser.newPage({ viewport: { width: 390, height: 844 } });
});

beforeEach(async () => {
  await page.goto(prototypeUrl);
  await page.evaluate(() => localStorage.clear());
  await page.goto(`${prototypeUrl}#home`);
});

after(async () => {
  await browser.close();
});

test('home presents one workout action and five primary destinations', async () => {
  const navLabels = await page.locator('[data-testid="bottom-nav"] [data-route]').allTextContents();
  assert.deepEqual(navLabels.map(value => value.trim()), ['首页', '计划', '训练', '饮食', '我的']);
  assert.equal(await page.locator('[data-testid="home-primary-action"]').count(), 1);
  assert.match(await page.locator('[data-testid="home-primary-action"]').innerText(), /开始训练/);
  assert.equal(await page.locator('text=首次配置').count(), 0);
});
```

- [ ] **Step 2: Run the test and verify it fails**

Run the global test command from this plan.

Expected: FAIL because `.scratch/fitness-app-mvp/健身App-单一主任务交互原型.html` does not exist or the selectors are missing.

- [ ] **Step 3: Create the semantic shell, design tokens, and state store**

Create the new HTML with this exact top-level contract and state API:

```html
<main class="app-shell" data-testid="app-shell">
  <section class="screen" id="screen" aria-live="polite"></section>
  <div id="overlay-root"></div>
  <div class="toast" id="toast" role="status" hidden></div>
</main>
```

```js
const STORAGE_KEY = 'ifitness-layout-prototype-v1';
const DEFAULT_STATE = {
  route: 'home',
  workoutStatus: 'idle',
  currentExerciseIndex: 0,
  completedSets: { smith: 0, dumbbell: 0 },
  targetSets: { smith: 4, dumbbell: 3 },
  weightKg: { smith: 70, dumbbell: 24 },
  reps: 8,
  feeling: '合适',
  restEndsAt: null,
  weeklyCompleted: 0,
  foodEntries: [],
  profile: { nickname: '我', goal: '增肌减脂', heightCm: 176, weightKg: 75 },
};

function loadState() {
  try {
    return { ...structuredClone(DEFAULT_STATE), ...JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') };
  } catch {
    return structuredClone(DEFAULT_STATE);
  }
}

let state = loadState();

function saveState() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    showToast('当前浏览器不会在刷新后保留演示数据');
  }
}
```

Define `:root` tokens for `--phone: #f5f2ec`, `--surface: #fffdfa`, `--surface-soft: #eee8dc`, `--ink: #11151b`, `--muted: #5f6874`, `--orange: #ff7426`, `--green: #24c869`, `--hero: #151b24`, three radius sizes, and `--touch: 48px`. Make `.app-shell` full-height and at most `430px` wide; on widths at or below `430px`, remove desktop shadows and fill the viewport.

- [ ] **Step 4: Implement hash routing, focused home, and five-tab navigation**

Implement the stable routing functions and exact five-item navigation:

```js
const PRIMARY_ROUTES = ['home', 'plan', 'training-prep', 'food', 'profile'];

function routeFromHash() {
  const route = location.hash.replace(/^#\/?/, '');
  return route || state.route || 'home';
}

function navigate(route) {
  state.route = route;
  saveState();
  location.hash = route;
  render();
}

function renderBottomNav() {
  const items = [
    ['home', 'home', '首页'],
    ['plan', 'calendar_month', '计划'],
    ['training-prep', 'fitness_center', '训练'],
    ['food', 'restaurant', '饮食'],
    ['profile', 'person', '我的'],
  ];
  return `<nav class="bottom-nav" data-testid="bottom-nav" aria-label="主导航">${items.map(([route, icon, label]) => `
    <button class="nav-item ${state.route === route ? 'active' : ''}" data-route="${route}" aria-current="${state.route === route ? 'page' : 'false'}">
      <span class="material-symbols-rounded" aria-hidden="true">${icon}</span><span>${label}</span>
    </button>`).join('')}</nav>`;
}

window.addEventListener('hashchange', () => {
  state.route = routeFromHash();
  saveState();
  render();
});
```

`renderHome()` must render one dark hero with `胸部力量 A`, `2 个动作 · 约 42 分钟`, `0 / 7 组`, the Smith GIF, exactly one `[data-testid="home-primary-action"]`, a compact `本周 0 / 3 次` strip, and two lightweight actions `记饮食` and `动作库`.

- [ ] **Step 5: Run the test and verify it passes**

Run the global test command.

Expected: `home presents one workout action and five primary destinations` passes.

- [ ] **Step 6: Commit the focused shell**

```bash
git add .scratch/fitness-app-mvp/健身App-单一主任务交互原型.html .scratch/fitness-app-mvp/健身App-单一主任务交互原型.test.cjs
git commit -m "feat: build focused fitness prototype shell"
```

---

### Task 2: Immersive workout state machine and completion feedback

**Files:**
- Modify: `.scratch/fitness-app-mvp/健身App-单一主任务交互原型.html`
- Modify: `.scratch/fitness-app-mvp/健身App-单一主任务交互原型.test.cjs`

**Interfaces:**
- Consumes: `state`, `saveState()`, `navigate()`, and the home action selectors from Task 1.
- Produces: `startWorkout()`, `completeSet()`, `skipRest()`, `finishWorkout()`, `renderTrainingPrep()`, `renderTrainingActive()`, and `renderWorkoutSummary()`.

- [ ] **Step 1: Add the failing workout-flow test**

Append this test:

```js
test('workout flow hides navigation, records a set, rests, and updates home', async () => {
  await page.getByTestId('home-primary-action').click();
  await page.getByTestId('start-workout').click();
  assert.equal(await page.getByTestId('bottom-nav').count(), 0);
  assert.equal(await page.getByTestId('complete-set').isVisible(), true);

  await page.getByTestId('weight-increase').click();
  await page.getByRole('button', { name: '吃力' }).click();
  await page.getByTestId('complete-set').click();
  assert.equal(await page.getByTestId('rest-panel').isVisible(), true);
  assert.match(await page.getByTestId('set-progress').innerText(), /1 \/ 7/);

  await page.getByTestId('skip-rest').click();
  await page.getByTestId('end-workout').click();
  await page.getByTestId('confirm-end-workout').click();
  assert.equal(await page.getByTestId('workout-summary').isVisible(), true);
  await page.getByTestId('summary-done').click();
  assert.match(await page.getByTestId('weekly-progress').innerText(), /1 \/ 3/);
});
```

- [ ] **Step 2: Run the test and verify it fails**

Run the global test command.

Expected: FAIL because the training selectors and state transitions are missing.

- [ ] **Step 3: Implement the workout transition functions**

Add these exact state transitions:

```js
function startWorkout() {
  state.workoutStatus = 'active';
  state.startedAt ||= Date.now();
  state.route = 'training-active';
  state.restEndsAt = null;
  saveState();
  navigate('training-active');
}

function completeSet() {
  const id = exercises[state.currentExerciseIndex].id;
  state.completedSets[id] = Math.min(state.targetSets[id], state.completedSets[id] + 1);
  state.logs ||= [];
  state.logs.push({ id, weightKg: state.weightKg[id], reps: state.reps, feeling: state.feeling, completedAt: Date.now() });
  state.workoutStatus = 'resting';
  state.restEndsAt = Date.now() + 60_000;
  saveState();
  render();
}

function skipRest() {
  state.workoutStatus = 'active';
  state.restEndsAt = null;
  const current = exercises[state.currentExerciseIndex];
  if (state.completedSets[current.id] >= state.targetSets[current.id] && state.currentExerciseIndex < exercises.length - 1) {
    state.currentExerciseIndex += 1;
  }
  saveState();
  render();
}

function finishWorkout() {
  state.workoutStatus = 'completed';
  state.weeklyCompleted = Math.min(3, state.weeklyCompleted + 1);
  state.finishedAt = Date.now();
  state.route = 'workout-summary';
  state.restEndsAt = null;
  saveState();
  navigate('workout-summary');
}
```

- [ ] **Step 4: Implement training-prep, active training, rest, confirmation, and summary views**

`renderTrainingActive()` must use a compact `4:3` GIF area, two exercise chips, set progress, weight and reps steppers, three `48px` feeling controls, and a fixed `[data-testid="complete-set"]`. Do not render `renderBottomNav()` when `state.route` is `training-active` or `workout-summary`; when `state.workoutStatus` is `resting`, keep the same route and replace the controls with the rest panel. Confirmation overlays cover the active screen without reintroducing navigation.

Use a timestamp-derived countdown:

```js
function restSecondsRemaining() {
  return Math.max(0, Math.ceil(((state.restEndsAt || Date.now()) - Date.now()) / 1000));
}
```

When the value reaches zero, call `skipRest()`. `finishWorkout()` is called only after a confirmation overlay with `[data-testid="confirm-end-workout"]`.

- [ ] **Step 5: Run the workout test and verify it passes**

Run the global test command.

Expected: both Task 1 and Task 2 tests pass.

- [ ] **Step 6: Commit the workout flow**

```bash
git add .scratch/fitness-app-mvp/健身App-单一主任务交互原型.html .scratch/fitness-app-mvp/健身App-单一主任务交互原型.test.cjs
git commit -m "feat: add immersive workout prototype flow"
```

---

### Task 3: Plan hierarchy and secondary action library

**Files:**
- Modify: `.scratch/fitness-app-mvp/健身App-单一主任务交互原型.html`
- Modify: `.scratch/fitness-app-mvp/健身App-单一主任务交互原型.test.cjs`

**Interfaces:**
- Consumes: primary routing and `exercises` from Tasks 1–2.
- Produces: `renderPlan()`, `renderPlanDetail()`, `renderLibrary()`, `renderExerciseDetail()`, `libraryQuery`, and `libraryFilter` state.

- [ ] **Step 1: Add failing plan and library tests**

```js
test('plan prioritizes the weekly schedule over monthly generation', async () => {
  await page.getByRole('button', { name: '计划' }).click();
  const scheduleTop = await page.getByTestId('weekly-schedule').boundingBox();
  const generatorTop = await page.getByTestId('monthly-plan-generator').boundingBox();
  assert.ok(scheduleTop.y < generatorTop.y);
  assert.equal(await page.getByText('休息日').count() > 0, true);
});

test('action library is secondary, searchable, and row-clickable', async () => {
  await page.getByRole('button', { name: '首页' }).click();
  await page.getByTestId('open-library').click();
  await page.getByTestId('library-search').fill('史密斯');
  assert.equal(await page.getByTestId('exercise-row').count(), 1);
  await page.getByTestId('exercise-row').click();
  assert.equal(await page.getByRole('heading', { name: '史密斯机卧推' }).isVisible(), true);
});
```

- [ ] **Step 2: Run the tests and verify they fail**

Expected: FAIL because the plan hierarchy and library selectors are missing.

- [ ] **Step 3: Implement the plan views**

`renderPlan()` must place `[data-testid="weekly-schedule"]` immediately after the week strip and `[data-testid="monthly-plan-generator"]` below the schedule. Use `休息日` for non-training dates. Clicking a planned session opens `renderPlanDetail()`; `新计划` opens a local editing overlay and saves the edited name/date into `state.plans`.

Use this schedule data:

```js
const weekPlan = [
  { date: '7/10', day: '五', title: '胸部力量 A', status: '今天' },
  { date: '7/11', day: '六', title: '下肢力量 A', status: '已计划' },
  { date: '7/12', day: '日', title: '休息日', status: '恢复' },
  { date: '7/13', day: '一', title: '背部拉力 A', status: '已计划' },
];
```

- [ ] **Step 4: Implement library search, filters, rows, and exercise detail**

Render search and filters from real-shaped exercise records:

```js
const exercises = [
  { id: 'smith', name: '史密斯机卧推', bodyPart: '胸', equipment: '史密斯机', sets: 4, weightKg: 70, image: '../../app/src/main/assets/exercise-media/gifs/0748-trqKQv2.gif' },
  { id: 'dumbbell', name: '哑铃卧推', bodyPart: '胸', equipment: '哑铃', sets: 3, weightKg: 24, image: '../../app/src/main/assets/exercise-media/gifs/0289-SpYC0Kp.gif' },
  { id: 'squat', name: '史密斯深蹲', bodyPart: '腿', equipment: '史密斯机', sets: 4, weightKg: 80, image: '../../app/src/main/assets/exercise-media/gifs/0770-jFtipLl.gif' },
  { id: 'pulldown', name: '高位下拉', bodyPart: '背', equipment: '器械', sets: 4, weightKg: 45, image: '../../app/src/main/assets/exercise-media/gifs/2330-LEprlgG.gif' },
];
```

Every result is one `[data-testid="exercise-row"]` button containing thumbnail, title, body part, and equipment with a chevron. Do not include a nested `查看` button.

- [ ] **Step 5: Run the plan and library tests and verify they pass**

Run the global test command.

Expected: four tests pass.

- [ ] **Step 6: Commit plan and library interactions**

```bash
git add .scratch/fitness-app-mvp/健身App-单一主任务交互原型.html .scratch/fitness-app-mvp/健身App-单一主任务交互原型.test.cjs
git commit -m "feat: add plan and library prototype interactions"
```

---

### Task 4: Food recording, profile summary, and consistent smart settings

**Files:**
- Modify: `.scratch/fitness-app-mvp/健身App-单一主任务交互原型.html`
- Modify: `.scratch/fitness-app-mvp/健身App-单一主任务交互原型.test.cjs`

**Interfaces:**
- Consumes: state persistence, route rendering, toast, and overlays from Tasks 1–3.
- Produces: `renderFood()`, `openFoodSheet()`, `saveFoodEntry()`, `renderProfile()`, `renderProfileEdit()`, `renderVenueSettings()`, and `renderSmartSettings()`.

- [ ] **Step 1: Add failing food and profile tests**

```js
test('adding a meal updates the daily summary and survives refresh', async () => {
  await page.getByRole('button', { name: '饮食' }).click();
  await page.getByTestId('add-meal').click();
  await page.getByRole('button', { name: '手动记录' }).click();
  await page.getByLabel('食物名称').fill('鸡胸肉饭');
  await page.getByLabel('热量 kcal').fill('520');
  await page.getByLabel('蛋白质 g').fill('42');
  await page.getByTestId('save-meal').click();
  assert.match(await page.getByTestId('calorie-total').innerText(), /520/);
  await page.reload();
  assert.equal(await page.getByText('鸡胸肉饭').isVisible(), true);
});

test('profile is summary-first and smart status is internally consistent', async () => {
  await page.getByRole('button', { name: '我的' }).click();
  assert.equal(await page.getByTestId('profile-summary').isVisible(), true);
  assert.equal(await page.locator('input').count(), 0);
  await page.getByRole('button', { name: '智能设置' }).click();
  const status = await page.getByTestId('smart-status').innerText();
  assert.ok(status === '未配置' || status === '已连接');
  assert.equal(status.includes('已连接') && (await page.getByText('密钥未保存').count() > 0), false);
});
```

- [ ] **Step 2: Run the tests and verify they fail**

Expected: FAIL because the food sheet, inputs, summary, and profile routes are missing.

- [ ] **Step 3: Implement the food summary, timeline, and add-meal sheet**

Use one primary `[data-testid="add-meal"]` button. The sheet first presents `拍照估算` and `手动记录`; photo mode returns a clearly labeled local demo draft. Manual mode validates name and calories beside the fields, preserves invalid input, and writes:

```js
function saveFoodEntry(form) {
  const entry = {
    id: crypto.randomUUID(),
    name: form.name.trim(),
    calories: Number(form.calories),
    protein: Number(form.protein || 0),
    carbs: Number(form.carbs || 0),
    fat: Number(form.fat || 0),
    loggedAt: new Date().toISOString(),
  };
  state.foodEntries.push(entry);
  saveState();
  closeOverlay();
  render();
}
```

`renderFood()` derives the four totals from `state.foodEntries` and displays entries newest first.

- [ ] **Step 4: Implement summary-first profile and full-screen settings routes**

`renderProfile()` renders a read-only `[data-testid="profile-summary"]`, three compact training metrics, and setting rows for `训练偏好`, `场地与器械`, `智能设置`, `数据备份`, and `关于`. Only `renderProfileEdit()` renders form inputs.

`renderSmartSettings()` derives one source of truth:

```js
const smartStatus = state.smartConfigured ? '已连接' : '未配置';
```

Do not render `密钥未保存` when `smartStatus === '已连接'`. `数据备份` and `重置演示数据` require confirmation; reset replaces state with `structuredClone(DEFAULT_STATE)` and removes `STORAGE_KEY`.

- [ ] **Step 5: Run all tests and verify they pass**

Run the global test command.

Expected: six tests pass.

- [ ] **Step 6: Commit food and profile interactions**

```bash
git add .scratch/fitness-app-mvp/健身App-单一主任务交互原型.html .scratch/fitness-app-mvp/健身App-单一主任务交互原型.test.cjs
git commit -m "feat: add food and profile prototype flows"
```

---

### Task 5: Responsive polish, accessibility contracts, screenshots, and final verification

**Files:**
- Modify: `.scratch/fitness-app-mvp/健身App-单一主任务交互原型.html`
- Modify: `.scratch/fitness-app-mvp/健身App-单一主任务交互原型.test.cjs`
- Create: `.scratch/run-evidence/interactive-layout-prototype/home-390.png`
- Create: `.scratch/run-evidence/interactive-layout-prototype/plan-390.png`
- Create: `.scratch/run-evidence/interactive-layout-prototype/training-390.png`
- Create: `.scratch/run-evidence/interactive-layout-prototype/food-390.png`
- Create: `.scratch/run-evidence/interactive-layout-prototype/profile-390.png`
- Create: `.scratch/run-evidence/interactive-layout-prototype/home-430.png`

**Interfaces:**
- Consumes: all rendered pages, selectors, state, and routes from Tasks 1–4.
- Produces: final accepted prototype, full Playwright test pass, mobile screenshots, and evidence of no console/resource errors.

- [ ] **Step 1: Add failing responsiveness, touch-target, persistence, and console tests**

```js
test('mobile layouts have no horizontal overflow and controls meet touch targets', async () => {
  for (const width of [390, 430]) {
    await page.setViewportSize({ width, height: 844 });
    await page.goto(`${prototypeUrl}#home`);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    assert.equal(overflow, 0);
    const undersized = await page.locator('button, a, input, [role="button"]').evaluateAll(nodes => nodes
      .filter(node => {
        const rect = node.getBoundingClientRect();
        return rect.width < 48 || rect.height < 48;
      })
      .map(node => ({ text: node.textContent.trim(), width: node.getBoundingClientRect().width, height: node.getBoundingClientRect().height })));
    assert.deepEqual(undersized, []);
  }
});

test('hash route and active workout survive reload without console errors', async () => {
  const errors = [];
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(`${prototypeUrl}#home`);
  await page.getByTestId('home-primary-action').click();
  await page.getByTestId('start-workout').click();
  await page.reload();
  assert.match(page.url(), /#training-active$/);
  assert.equal(await page.getByTestId('complete-set').isVisible(), true);
  assert.deepEqual(errors, []);
});
```

- [ ] **Step 2: Run the tests and verify they fail on unfinished polish**

Expected: FAIL with exact undersized controls, overflow, or missing persistence behavior.

- [ ] **Step 3: Apply final responsive and accessibility fixes**

Use these non-negotiable CSS rules:

```css
button, a, input, [role="button"] { min-height: 48px; }
.app-shell { width: min(430px, 100vw); min-height: 100dvh; overflow-x: clip; }
.screen { padding: 20px 18px calc(104px + env(safe-area-inset-bottom)); }
.training-active .screen { padding-bottom: calc(92px + env(safe-area-inset-bottom)); }
@media (max-width: 430px) {
  body { background: var(--phone); }
  .app-shell { width: 100%; box-shadow: none; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: .01ms !important; transition-duration: .01ms !important; }
}
```

Ensure every meaningful image has an accurate Chinese `alt`; decorative icons use `aria-hidden="true"`; every icon-only action has an `aria-label`; form errors are linked with `aria-describedby`.

- [ ] **Step 4: Capture accepted screenshots from the tested browser state**

Add a screenshot test that creates `.scratch/run-evidence/interactive-layout-prototype` and saves the named files after navigating to each route. Reset state before the sequence; for the active-training screenshot, enter through the home action and start button rather than forcing state.

Use:

```js
const evidenceDir = '.scratch/run-evidence/interactive-layout-prototype';
await fs.promises.mkdir(evidenceDir, { recursive: true });
await page.setViewportSize({ width: 390, height: 844 });
await page.goto(`${prototypeUrl}#home`);
await page.screenshot({ path: `${evidenceDir}/home-390.png`, fullPage: true });
```

Repeat for plan, training, food, profile, and the 430px home viewport. Inspect every PNG and reject screenshots that are blank, loading, cropped, or show the wrong state.

- [ ] **Step 5: Run the full automated verification**

Run the global test command.

Expected: all tests pass with `fail 0`; the HTTP log has no `404` lines:

```bash
! rg -n ' 404 ' /tmp/ifitness-prototype-http.log
```

- [ ] **Step 6: Inspect the prototype and evidence manually**

Open `http://127.0.0.1:8087/.scratch/fitness-app-mvp/%E5%81%A5%E8%BA%ABApp-%E5%8D%95%E4%B8%80%E4%B8%BB%E4%BB%BB%E5%8A%A1%E4%BA%A4%E4%BA%92%E5%8E%9F%E5%9E%8B.html` and verify:

- home has one primary action and five tabs;
- plan schedule precedes generation;
- training active hides bottom navigation and keeps `完成本组` visible;
- food and profile match the design spec;
- GIF assets animate and text is not clipped;
- desktop and mobile previews contain no unintended horizontal scroll.

- [ ] **Step 7: Commit the verified prototype**

```bash
git add .scratch/fitness-app-mvp/健身App-单一主任务交互原型.html .scratch/fitness-app-mvp/健身App-单一主任务交互原型.test.cjs .scratch/run-evidence/interactive-layout-prototype
git commit -m "test: verify interactive fitness prototype"
```

## Plan Self-Review

- Spec coverage: Tasks 1–5 cover five-tab navigation, one-task home, immersive training, plan hierarchy, secondary action library, food flow, summary-first profile, smart status consistency, persistence, accessibility, responsive layouts, and screenshot evidence.
- Placeholder scan: no implementation step contains unfinished markers or undefined follow-up wording.
- Type consistency: all tasks use the same `state`, `STORAGE_KEY`, route names, workout statuses, exercise IDs, and test selectors defined in Task 1 or explicitly introduced by the consuming task.
