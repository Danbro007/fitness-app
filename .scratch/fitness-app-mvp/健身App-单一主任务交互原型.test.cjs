const { test, before, beforeEach, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { chromium } = require('playwright');

const prototypeUrl = 'http://127.0.0.1:8087/.scratch/fitness-app-mvp/%E5%81%A5%E8%BA%ABApp-%E5%8D%95%E4%B8%80%E4%B8%BB%E4%BB%BB%E5%8A%A1%E4%BA%A4%E4%BA%92%E5%8E%9F%E5%9E%8B.html';
let browser;
let page;

function browserLaunchOptions() {
  const options = { headless: true };
  if (process.env.PLAYWRIGHT_CHROME_PATH) {
    options.executablePath = process.env.PLAYWRIGHT_CHROME_PATH;
  }
  return options;
}

function rgbChannels(value) {
  const channels = value.match(/[\d.]+/g)?.slice(0, 3).map(Number);
  assert.equal(channels?.length, 3, `Expected an rgb color, received: ${value}`);
  return channels;
}

function relativeLuminance(value) {
  const [red, green, blue] = rgbChannels(value).map(channel => {
    const normalized = channel / 255;
    return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4;
  });
  return (0.2126 * red) + (0.7152 * green) + (0.0722 * blue);
}

function contrastRatio(foreground, background) {
  const lighter = Math.max(relativeLuminance(foreground), relativeLuminance(background));
  const darker = Math.min(relativeLuminance(foreground), relativeLuminance(background));
  return (lighter + 0.05) / (darker + 0.05);
}

before(async () => {
  browser = await chromium.launch(browserLaunchOptions());
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

test('green marks selection and progress while orange stays on the primary action', async () => {
  const palette = await page.evaluate(() => ({
    activeNavigation: getComputedStyle(document.querySelector('.nav-item.active .nav-icon')).backgroundColor,
    progress: getComputedStyle(document.querySelector('.hero-progress strong')).color,
    statusMarker: getComputedStyle(document.querySelector('.hero-eyebrow'), '::before').backgroundColor,
    primaryAction: getComputedStyle(document.querySelector('[data-testid="home-primary-action"]')).backgroundColor,
  }));

  assert.deepEqual(palette, {
    activeNavigation: 'rgb(36, 200, 105)',
    progress: 'rgb(36, 200, 105)',
    statusMarker: 'rgb(36, 200, 105)',
    primaryAction: 'rgb(255, 116, 38)',
  });
});

test('browser executable path override is optional', () => {
  const originalPath = process.env.PLAYWRIGHT_CHROME_PATH;

  try {
    delete process.env.PLAYWRIGHT_CHROME_PATH;
    assert.deepEqual(browserLaunchOptions(), { headless: true });

    process.env.PLAYWRIGHT_CHROME_PATH = '/tmp/portable-chrome';
    assert.deepEqual(browserLaunchOptions(), {
      headless: true,
      executablePath: '/tmp/portable-chrome',
    });
  } finally {
    if (originalPath === undefined) {
      delete process.env.PLAYWRIGHT_CHROME_PATH;
    } else {
      process.env.PLAYWRIGHT_CHROME_PATH = originalPath;
    }
  }
});

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

test('neutral and status labels keep semantic colors with accessible contrast', async () => {
  await page.getByTestId('home-primary-action').click();
  const prep = await page.evaluate(() => {
    const tokenColor = token => {
      const probe = document.createElement('span');
      probe.style.color = `var(${token})`;
      document.body.append(probe);
      const color = getComputedStyle(probe).color;
      probe.remove();
      return color;
    };
    return {
      color: getComputedStyle(document.querySelector('.page-kicker')).color,
      background: getComputedStyle(document.querySelector('.app-shell')).backgroundColor,
      muted: tokenColor('--muted'),
      brightGreen: tokenColor('--green'),
      greenText: tokenColor('--green-text'),
    };
  });

  await page.getByTestId('start-workout').click();
  await page.getByTestId('complete-set').click();
  const rest = await page.evaluate(() => ({
    color: getComputedStyle(document.querySelector('.rest-panel > span')).color,
    background: getComputedStyle(document.querySelector('.rest-panel')).backgroundColor,
  }));

  await page.getByTestId('skip-rest').click();
  await page.getByTestId('end-workout').click();
  await page.getByTestId('confirm-end-workout').click();
  const summary = await page.evaluate(() => ({
    color: getComputedStyle(document.querySelector('.workout-summary-screen .page-kicker')).color,
    background: getComputedStyle(document.querySelector('.app-shell')).backgroundColor,
  }));

  assert.equal(prep.color, prep.muted);
  assert.ok(contrastRatio(prep.color, prep.background) >= 4.5);
  assert.notEqual(prep.greenText, prep.brightGreen);
  assert.equal(rest.color, prep.greenText);
  assert.ok(contrastRatio(rest.color, rest.background) >= 4.5);
  assert.equal(summary.color, prep.greenText);
  assert.ok(contrastRatio(summary.color, summary.background) >= 4.5);
});

test('plan prioritizes the weekly schedule over monthly generation', async () => {
  await page.getByRole('button', { name: '计划' }).click();
  const scheduleTop = await page.getByTestId('weekly-schedule').boundingBox();
  const generatorTop = await page.getByTestId('monthly-plan-generator').boundingBox();
  assert.ok(scheduleTop.y < generatorTop.y);
  assert.equal(await page.getByText('休息日').count() > 0, true);
});

test('plan titles remain literal text after saving and refreshing', async () => {
  const unsafeTitle = '力量 "A" <b data-testid="injected-plan-title">偷换</b>';
  await page.getByRole('button', { name: '计划' }).click();
  await page.getByRole('button', { name: '新计划' }).click();
  await page.getByTestId('plan-name-input').fill(unsafeTitle);
  await page.getByRole('button', { name: '保存计划' }).click();
  await page.reload();

  const savedTitles = await page.locator('.plan-row-copy strong').allTextContents();
  assert.equal(savedTitles.includes(unsafeTitle), true);
  assert.equal(await page.getByTestId('injected-plan-title').count(), 0);

  await page.locator('[data-plan-index]').last().click();
  assert.equal(await page.getByRole('heading', { name: unsafeTitle, exact: true }).isVisible(), true);
  await page.getByRole('button', { name: '编辑计划' }).click();
  assert.equal(await page.getByTestId('plan-name-input').inputValue(), unsafeTitle);
});

test('plan editor traps focus, makes the background inert, and restores the opener', async () => {
  await page.getByRole('button', { name: '计划' }).click();
  const opener = page.getByRole('button', { name: '新计划' });
  await opener.click();

  const nameInput = page.getByTestId('plan-name-input');
  const saveButton = page.getByRole('button', { name: '保存计划' });
  assert.equal(await nameInput.evaluate(element => element === document.activeElement), true);
  assert.equal(await page.locator('#screen').evaluate(element => element.inert), true);

  await saveButton.focus();
  await page.keyboard.press('Tab');
  assert.equal(await nameInput.evaluate(element => element === document.activeElement), true);

  await nameInput.focus();
  await page.keyboard.press('Shift+Tab');
  assert.equal(await saveButton.evaluate(element => element === document.activeElement), true);

  await page.getByRole('button', { name: '取消' }).click();
  assert.equal(await page.locator('#screen').evaluate(element => element.inert), false);
  assert.equal(await opener.evaluate(element => element === document.activeElement), true);
});

test('browser navigation tears down the plan editor and clears inert state', async () => {
  await page.getByRole('button', { name: '计划' }).click();
  await page.getByRole('button', { name: '新计划' }).click();
  assert.equal(await page.locator('#screen').evaluate(element => element.inert), true);

  await page.goBack();
  assert.equal(new URL(page.url()).hash, '#home');
  assert.equal(await page.locator('.plan-editor-card').count(), 0);
  assert.equal(await page.locator('#screen').evaluate(element => element.inert), false);

  const homeAction = page.getByTestId('home-primary-action');
  assert.equal(await homeAction.isVisible(), true);
  await homeAction.focus();
  assert.equal(await homeAction.evaluate(element => element === document.activeElement), true);
});

test('action library is secondary, searchable, and row-clickable', async () => {
  await page.getByRole('button', { name: '首页' }).click();
  await page.getByTestId('open-library').click();
  await page.getByTestId('library-search').fill('史密斯');
  assert.equal(await page.getByTestId('exercise-row').count(), 1);
  await page.getByTestId('exercise-row').click();
  assert.equal(await page.getByRole('heading', { name: '史密斯机卧推' }).isVisible(), true);
});

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
