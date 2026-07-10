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

function pngDimensions(buffer) {
  assert.equal(buffer.subarray(1, 4).toString(), 'PNG');
  return {
    width: buffer.readUInt32BE(16),
    height: buffer.readUInt32BE(20),
  };
}

before(async () => {
  browser = await chromium.launch(browserLaunchOptions());
  page = await browser.newPage({ viewport: { width: 390, height: 844 } });
});

beforeEach(async () => {
  await page.goto(prototypeUrl);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.goto(`${prototypeUrl}#home`);
});

after(async () => {
  await browser.close();
});

test('home presents one workout action and five primary destinations', async () => {
  const navLabels = await page.locator('[data-testid="bottom-nav"] [data-route] > span:last-child').allTextContents();
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

test('invalid manual meal values remain visible with linked field errors', async () => {
  await page.getByRole('button', { name: '饮食', exact: true }).click();
  await page.getByTestId('add-meal').click();
  await page.getByRole('button', { name: '手动记录' }).click();

  const nameInput = page.getByLabel('食物名称');
  const calorieInput = page.getByLabel('热量 kcal');
  await nameInput.fill('   ');
  await calorieInput.fill('0');
  await page.getByTestId('save-meal').click();

  assert.equal(await nameInput.inputValue(), '   ');
  assert.equal(await calorieInput.inputValue(), '0');
  assert.equal(await nameInput.getAttribute('aria-describedby'), 'meal-name-error');
  assert.equal(await calorieInput.getAttribute('aria-describedby'), 'meal-calories-error');
  assert.equal(await nameInput.getAttribute('aria-invalid'), 'true');
  assert.equal(await calorieInput.getAttribute('aria-invalid'), 'true');
  assert.equal(await page.locator('#meal-name-error').innerText(), '请输入食物名称');
  assert.equal(await page.locator('#meal-calories-error').innerText(), '请输入大于 0 的热量');
});

test('hostile meal names render literally and persist without injecting elements', async () => {
  const unsafeName = '主食 <b data-testid="injected-meal-name">篡改</b>';
  await page.getByRole('button', { name: '饮食', exact: true }).click();
  await page.getByTestId('add-meal').click();
  await page.getByRole('button', { name: '手动记录' }).click();
  await page.getByLabel('食物名称').fill(unsafeName);
  await page.getByLabel('热量 kcal').fill('360');
  await page.getByTestId('save-meal').click();

  assert.equal(await page.getByText(unsafeName, { exact: true }).isVisible(), true);
  assert.equal(await page.getByTestId('injected-meal-name').count(), 0);
  await page.reload();
  assert.equal(await page.getByText(unsafeName, { exact: true }).isVisible(), true);
  assert.equal(await page.getByTestId('injected-meal-name').count(), 0);
});

test('photo estimation is explicitly a local demo draft', async () => {
  await page.getByRole('button', { name: '饮食', exact: true }).click();
  await page.getByTestId('add-meal').click();
  await page.getByRole('button', { name: '拍照估算' }).click();

  const draft = page.getByTestId('local-photo-draft');
  assert.equal(await draft.isVisible(), true);
  assert.match(await draft.innerText(), /本地演示草稿/);
  assert.equal(await page.getByText(/不上传图片/).isVisible(), true);
});

test('smart status stays consistent through connect, refresh, and disconnect', async () => {
  await page.getByRole('button', { name: '我的', exact: true }).click();
  await page.getByRole('button', { name: /智能设置/ }).click();
  assert.equal(await page.getByTestId('smart-status').innerText(), '未配置');

  await page.getByRole('button', { name: '启用演示连接' }).click();
  assert.equal(await page.getByTestId('smart-status').innerText(), '已连接');
  assert.equal(await page.getByText('密钥未保存').count(), 0);
  await page.reload();
  assert.equal(await page.getByTestId('smart-status').innerText(), '已连接');
  assert.equal(await page.getByText('密钥未保存').count(), 0);

  await page.getByRole('button', { name: '断开演示连接' }).click();
  assert.equal(await page.getByTestId('smart-status').innerText(), '未配置');
  assert.equal(await page.getByText('密钥未保存').isVisible(), true);
});

test('reset requires confirmation and removes persisted prototype state', async () => {
  await page.getByRole('button', { name: '我的', exact: true }).click();
  await page.getByRole('button', { name: /数据备份/ }).click();
  await page.getByRole('button', { name: '重置演示数据' }).click();

  assert.equal(await page.getByRole('heading', { name: '重置演示数据？' }).isVisible(), true);
  assert.notEqual(await page.evaluate(() => localStorage.getItem('ifitness-layout-prototype-v1')), null);
  await page.getByRole('button', { name: '取消' }).click();
  assert.notEqual(await page.evaluate(() => localStorage.getItem('ifitness-layout-prototype-v1')), null);

  await page.getByRole('button', { name: '重置演示数据' }).click();
  await page.getByRole('button', { name: '确认重置' }).click();
  assert.equal(await page.evaluate(() => localStorage.getItem('ifitness-layout-prototype-v1')), null);
  assert.equal(new URL(page.url()).hash, '#home');
  assert.equal(await page.getByTestId('home-screen').isVisible(), true);
});

test('food sheet traps focus, makes the background inert, and restores its opener', async () => {
  await page.getByRole('button', { name: '饮食', exact: true }).click();
  const opener = page.getByTestId('add-meal');
  await opener.click();

  const firstAction = page.getByRole('button', { name: '拍照估算' });
  const lastAction = page.getByRole('button', { name: '取消' });
  assert.equal(await firstAction.evaluate(element => element === document.activeElement), true);
  assert.equal(await page.locator('#screen').evaluate(element => element.inert), true);

  await lastAction.focus();
  await page.keyboard.press('Tab');
  assert.equal(await firstAction.evaluate(element => element === document.activeElement), true);
  await firstAction.focus();
  await page.keyboard.press('Shift+Tab');
  assert.equal(await lastAction.evaluate(element => element === document.activeElement), true);

  await lastAction.click();
  assert.equal(await page.locator('#screen').evaluate(element => element.inert), false);
  assert.equal(await opener.evaluate(element => element === document.activeElement), true);
});

test('mobile layouts have no horizontal overflow and controls meet touch targets at 390px and 430px', async () => {
  const layouts = {};

  for (const width of [390, 430]) {
    await page.setViewportSize({ width, height: 844 });
    await page.goto(`${prototypeUrl}#home`);
    layouts[width] = await page.evaluate(() => ({
      overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      undersized: [...document.querySelectorAll('button, a, input, [role="button"]')]
        .filter(node => {
          const rect = node.getBoundingClientRect();
          return rect.width < 48 || rect.height < 48;
        })
        .map(node => {
          const rect = node.getBoundingClientRect();
          return { text: node.textContent.trim(), width: rect.width, height: rect.height };
        }),
    }));
  }

  assert.equal(layouts[390].overflow, 0);
  assert.deepEqual(layouts[390].undersized, []);
  assert.equal(layouts[430].overflow, 0);
  assert.deepEqual(layouts[430].undersized, []);
});

test('hash route and active workout survive reload without console or resource errors', async () => {
  const errors = [];
  const resourceErrors = [];
  const consoleHandler = message => {
    if (message.type() === 'error') errors.push(message.text());
  };
  const pageErrorHandler = error => errors.push(error.message);
  const requestFailedHandler = request => resourceErrors.push(`${request.url()} ${request.failure()?.errorText || 'failed'}`);
  const responseHandler = response => {
    if (response.status() >= 400) resourceErrors.push(`${response.status()} ${response.url()}`);
  };

  page.on('console', consoleHandler);
  page.on('pageerror', pageErrorHandler);
  page.on('requestfailed', requestFailedHandler);
  page.on('response', responseHandler);

  try {
    await page.goto(`${prototypeUrl}#home`);
    await page.getByTestId('home-primary-action').click();
    await page.getByTestId('start-workout').click();
    await page.reload();
    assert.match(page.url(), /#training-active$/);
    assert.equal(await page.getByTestId('complete-set').isVisible(), true);
    assert.deepEqual(errors, []);
    assert.deepEqual(resourceErrors, []);
  } finally {
    page.off('console', consoleHandler);
    page.off('pageerror', pageErrorHandler);
    page.off('requestfailed', requestFailedHandler);
    page.off('response', responseHandler);
  }
});

test('neutral states and ordinary food values do not use success green', async () => {
  await page.getByRole('button', { name: '饮食', exact: true }).click();
  const foodPalette = await page.evaluate(() => {
    const tokenColor = token => {
      const probe = document.createElement('span');
      probe.style.color = `var(${token})`;
      document.body.append(probe);
      const color = getComputedStyle(probe).color;
      probe.remove();
      return color;
    };
    return {
      calorie: getComputedStyle(document.querySelector('[data-testid="calorie-total"]')).color,
      green: tokenColor('--green'),
      greenText: tokenColor('--green-text'),
      muted: tokenColor('--muted'),
    };
  });
  assert.notEqual(foodPalette.calorie, foodPalette.green);
  assert.notEqual(foodPalette.calorie, foodPalette.greenText);

  await page.getByTestId('add-meal').click();
  const photoChoice = page.getByRole('button', { name: '拍照估算' });
  const manualChoice = page.getByRole('button', { name: '手动记录' });
  assert.equal(
    await photoChoice.evaluate(element => getComputedStyle(element).backgroundColor),
    await manualChoice.evaluate(element => getComputedStyle(element).backgroundColor),
  );

  await photoChoice.click();
  const draftColor = await page.getByTestId('local-photo-draft').locator('span').first()
    .evaluate(element => getComputedStyle(element).color);
  assert.equal(draftColor, foodPalette.muted);

  await page.getByRole('button', { name: '确认加入' }).click();
  assert.equal(
    await page.locator('.food-entry > em').evaluate(element => getComputedStyle(element).color),
    foodPalette.muted,
  );
  await page.getByRole('button', { name: '我的', exact: true }).click();
  await page.getByRole('button', { name: /智能设置/ }).click();
  const smartStatus = page.getByTestId('smart-status');
  assert.equal(await smartStatus.innerText(), '未配置');
  assert.equal(await smartStatus.evaluate(element => getComputedStyle(element).color), foodPalette.muted);

  await page.getByRole('button', { name: '启用演示连接' }).click();
  assert.equal(await smartStatus.evaluate(element => getComputedStyle(element).color), foodPalette.greenText);
});

test('meaningful images, decorative icons, icon actions, and form errors expose accessible contracts', async () => {
  const assertCurrentImagesHaveAlt = async () => {
    const missing = await page.locator('img').evaluateAll(images => images
      .filter(image => !image.alt.trim())
      .map(image => image.getAttribute('src')));
    assert.deepEqual(missing, []);
  };

  await assertCurrentImagesHaveAlt();
  await page.getByTestId('home-primary-action').click();
  await assertCurrentImagesHaveAlt();
  await page.getByTestId('start-workout').click();
  await assertCurrentImagesHaveAlt();
  for (const label of ['减少重量', '增加重量', '减少次数', '增加次数']) {
    assert.equal(await page.getByRole('button', { name: label }).count(), 1);
  }

  await page.getByTestId('end-workout').click();
  await page.getByRole('button', { name: '继续训练' }).click();
  await page.goto(`${prototypeUrl}#library`);
  await assertCurrentImagesHaveAlt();

  const visibleDecorativeIcons = await page.locator('.nav-icon, .exercise-row-chevron, .summary-mark, .placeholder-icon, .week-dots')
    .evaluateAll(nodes => nodes.filter(node => node.getBoundingClientRect().width > 0 && node.getAttribute('aria-hidden') !== 'true').length);
  assert.equal(visibleDecorativeIcons, 0);

  await page.goto(`${prototypeUrl}#food`);
  await page.getByTestId('add-meal').click();
  await page.getByRole('button', { name: '手动记录' }).click();
  assert.equal(await page.getByLabel('食物名称').getAttribute('aria-describedby'), 'meal-name-error');
  assert.equal(await page.getByLabel('热量 kcal').getAttribute('aria-describedby'), 'meal-calories-error');
});

test('generic workout confirmation traps focus, inerts the background, and restores the opener', async () => {
  await page.getByTestId('home-primary-action').click();
  await page.getByTestId('start-workout').click();
  const opener = page.getByTestId('end-workout');
  await opener.click();

  const firstAction = page.getByRole('button', { name: '继续训练' });
  const lastAction = page.getByTestId('confirm-end-workout');
  assert.equal(await firstAction.evaluate(element => element === document.activeElement), true);
  assert.equal(await page.locator('#screen').evaluate(element => element.inert), true);

  await lastAction.focus();
  await page.keyboard.press('Tab');
  assert.equal(await firstAction.evaluate(element => element === document.activeElement), true);
  await firstAction.focus();
  await page.keyboard.press('Shift+Tab');
  assert.equal(await lastAction.evaluate(element => element === document.activeElement), true);

  await firstAction.click();
  assert.equal(await page.locator('#screen').evaluate(element => element.inert), false);
  assert.equal(await opener.evaluate(element => element === document.activeElement), true);
});

test('workout summary route explicitly hides bottom navigation', async () => {
  await page.getByTestId('home-primary-action').click();
  await page.getByTestId('start-workout').click();
  await page.getByTestId('end-workout').click();
  await page.getByTestId('confirm-end-workout').click();

  assert.match(page.url(), /#workout-summary$/);
  assert.equal(await page.getByTestId('workout-summary').isVisible(), true);
  assert.equal(await page.getByTestId('bottom-nav').count(), 0);
});

test('route navigation renders once and announces through a dedicated live region', async () => {
  const result = await page.evaluate(async () => {
    const screen = document.getElementById('screen');
    let replacements = 0;
    const observer = new MutationObserver(records => {
      replacements += records.filter(record => record.type === 'childList' && record.target === screen).length;
    });
    observer.observe(screen, { childList: true });
    document.querySelector('[data-route="plan"]').click();
    await new Promise(resolve => setTimeout(resolve, 80));
    observer.disconnect();
    const announcer = document.getElementById('route-announcer');
    return {
      replacements,
      screenLive: screen.getAttribute('aria-live'),
      announcerLive: announcer?.getAttribute('aria-live'),
      announcement: announcer?.textContent,
    };
  });

  assert.equal(result.replacements, 1);
  assert.equal(result.screenLive, null);
  assert.equal(result.announcerLive, 'polite');
  assert.match(result.announcement || '', /计划/);
});

test('idle home CTA survives reload and opens workout preparation', async () => {
  await page.reload();
  await page.goto(`${prototypeUrl}#home`);

  const action = page.getByTestId('home-primary-action');
  assert.equal(await action.innerText(), '开始训练');
  assert.match(await page.getByTestId('home-workout-summary').innerText(), /2 个动作/);
  assert.equal(await page.locator('[data-testid="home-primary-action"]').count(), 1);
  await action.click();
  assert.match(page.url(), /#training-prep$/);
});

test('active and resting home CTA survives reload and continues the active workout', async () => {
  await page.getByTestId('home-primary-action').click();
  await page.getByTestId('start-workout').click();
  await page.reload();
  await page.goto(`${prototypeUrl}#home`);

  const activeAction = page.getByTestId('home-primary-action');
  assert.equal(await activeAction.innerText(), '继续训练');
  assert.match(await page.getByTestId('home-workout-summary').innerText(), /训练进行中/);
  await activeAction.click();
  assert.match(page.url(), /#training-active$/);

  await page.getByTestId('complete-set').click();
  await page.goto(`${prototypeUrl}#home`);
  await page.reload();
  const restingAction = page.getByTestId('home-primary-action');
  assert.equal(await restingAction.innerText(), '继续训练');
  await restingAction.click();
  assert.equal(await page.getByTestId('rest-panel').isVisible(), true);
});

test('completed home CTA survives reload and exposes the workout result and next training', async () => {
  await page.getByTestId('home-primary-action').click();
  await page.getByTestId('start-workout').click();
  await page.getByTestId('complete-set').click();
  await page.getByTestId('skip-rest').click();
  await page.getByTestId('end-workout').click();
  await page.getByTestId('confirm-end-workout').click();
  await page.reload();
  await page.goto(`${prototypeUrl}#home`);

  const action = page.getByTestId('home-primary-action');
  assert.equal(await action.innerText(), '查看训练总结');
  assert.match(await page.getByTestId('home-workout-summary').innerText(), /今日训练已完成/);
  assert.match(await page.getByTestId('home-workout-summary').innerText(), /下次训练/);
  assert.equal(await page.locator('[data-testid="home-primary-action"]').count(), 1);
  await action.click();
  assert.match(page.url(), /#workout-summary$/);
  assert.equal(await page.getByTestId('workout-summary').isVisible(), true);
});

test('bottom navigation loads the local Material Symbols Rounded font and real ligatures', async () => {
  const fontResponses = [];
  const responseHandler = response => {
    if (response.url().includes('/prototype-assets/material-symbols-rounded.woff2')) {
      fontResponses.push({ status: response.status(), url: response.url() });
    }
  };
  page.on('response', responseHandler);

  try {
    await page.reload();
    await page.evaluate(() => document.fonts.ready);
    const fontState = await page.evaluate(() => ({
      loaded: [...document.fonts].some(font => font.family.includes('Material Symbols Rounded') && font.status === 'loaded'),
      check: document.fonts.check('24px "Material Symbols Rounded"'),
      ligatures: [...document.querySelectorAll('.nav-icon')].map(icon => icon.textContent.trim()),
      generatedContent: [...document.querySelectorAll('.nav-icon')].map(icon => getComputedStyle(icon, '::before').content),
    }));

    assert.deepEqual(fontResponses, [{
      status: 200,
      url: `${new URL(prototypeUrl).origin}/.scratch/fitness-app-mvp/prototype-assets/material-symbols-rounded.woff2`,
    }]);
    assert.equal(fontState.loaded, true);
    assert.equal(fontState.check, true);
    assert.deepEqual(fontState.ligatures, ['home', 'calendar_month', 'fitness_center', 'restaurant', 'person']);
    assert.deepEqual(fontState.generatedContent, ['none', 'none', 'none', 'none', 'none']);
  } finally {
    page.off('response', responseHandler);
  }
});

test('training preparation and plan editor provide accessible library entry points', async () => {
  await page.getByTestId('home-primary-action').click();
  await page.getByRole('button', { name: '从动作库选择' }).click();
  assert.match(page.url(), /#library$/);
  assert.equal(await page.getByTestId('library-screen').isVisible(), true);
  await page.getByRole('button', { name: '返回训练准备' }).click();
  assert.match(page.url(), /#training-prep$/);

  await page.goto(`${prototypeUrl}#plan`);
  await page.getByRole('button', { name: '新计划' }).click();
  await page.getByRole('button', { name: '浏览动作库' }).click();
  assert.match(page.url(), /#library$/);
  assert.equal(await page.locator('#screen').evaluate(element => element.inert), false);
  await page.getByRole('button', { name: '返回计划' }).click();
  assert.match(page.url(), /#plan$/);
});

test('exercise detail can be used for this workout', async () => {
  await page.getByTestId('open-library').click();
  await page.getByTestId('exercise-row').first().click();
  const exerciseName = await page.getByRole('heading', { level: 1 }).innerText();
  await page.getByRole('button', { name: '用于本次训练' }).click();

  assert.match(page.url(), /#training-prep$/);
  assert.equal(await page.getByTestId('training-prep-screen').isVisible(), true);
  assert.equal(await page.getByText(exerciseName, { exact: true }).count() > 0, true);
});

test('active workout GIF expands through the accessible overlay lifecycle', async () => {
  await page.getByTestId('home-primary-action').click();
  await page.getByTestId('start-workout').click();
  const opener = page.getByRole('button', { name: /放大查看史密斯机卧推动作演示/ });
  await opener.click();

  assert.equal(await page.locator('#screen').evaluate(element => element.inert), true);
  assert.equal(await page.getByRole('dialog', { name: '史密斯机卧推动作演示' }).isVisible(), true);
  assert.equal(await page.getByRole('dialog').getByRole('img', { name: '史密斯机卧推动作演示' }).isVisible(), true);
  const closeButton = page.getByRole('button', { name: '关闭动作演示' });
  assert.equal(await closeButton.evaluate(element => element === document.activeElement), true);
  await closeButton.click();
  assert.equal(await page.locator('#screen').evaluate(element => element.inert), false);
  assert.equal(await opener.evaluate(element => element === document.activeElement), true);
});

test('library filter keeps a full accessible row with structured exercise metadata', async () => {
  await page.getByTestId('open-library').click();
  await page.getByRole('button', { name: '背', exact: true }).click();

  const row = page.getByTestId('exercise-row');
  assert.equal(await row.count(), 1);
  assert.equal(await row.evaluate(element => element.tagName), 'BUTTON');
  assert.equal(await row.locator('img').getAttribute('alt'), '高位下拉动作演示');
  assert.equal(await row.getByText('背', { exact: true }).count(), 1);
  assert.equal(await row.getByText('器械', { exact: true }).count(), 1);
  assert.equal(await row.locator('.exercise-row-chevron').innerText(), '›');
  assert.equal(await row.locator('button').count(), 0);
  assert.equal(await row.getByRole('button', { name: /查看/ }).count(), 0);
});

test('accepted mobile routes produce complete screenshot evidence through the real workout flow', async () => {
  const evidenceDir = '.scratch/run-evidence/interactive-layout-prototype';
  const captured = [];
  await fs.promises.mkdir(evidenceDir, { recursive: true });

  const assertNeutralBackground = async expectedTestId => {
    const background = await page.evaluate(() => {
      const probe = document.createElement('span');
      probe.style.backgroundColor = 'var(--phone)';
      document.body.append(probe);
      const phone = getComputedStyle(probe).backgroundColor;
      probe.remove();
      return {
        phone,
        body: getComputedStyle(document.body).backgroundColor,
        appShell: getComputedStyle(document.querySelector('.app-shell')).backgroundColor,
        screen: getComputedStyle(document.getElementById('screen')).backgroundColor,
        trainingClass: document.querySelector('.app-shell').classList.contains('training-active'),
      };
    });
    assert.equal(await page.getByTestId(expectedTestId).isVisible(), true);
    assert.deepEqual(background, {
      phone: 'rgb(245, 242, 236)',
      body: 'rgb(245, 242, 236)',
      appShell: 'rgb(245, 242, 236)',
      screen: 'rgb(245, 242, 236)',
      trainingClass: false,
    });
  };

  const waitForImages = async () => {
    await page.waitForTimeout(320);
    await page.evaluate(async () => { await document.fonts.ready; });
    await page.waitForFunction(() => [...document.images].every(image => image.complete && image.naturalWidth > 0));
  };
  const capture = async (name, expectedWidth) => {
    await waitForImages();
    const path = `${evidenceDir}/${name}`;
    await page.screenshot({ path, fullPage: true });
    const buffer = await fs.promises.readFile(path);
    const dimensions = pngDimensions(buffer);
    assert.equal(dimensions.width, expectedWidth);
    assert.ok(dimensions.height >= 844, `${name} height was ${dimensions.height}`);
    assert.ok(buffer.byteLength > 10_000, `${name} was unexpectedly small`);
    captured.push(name);
  };

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(prototypeUrl);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.goto(`${prototypeUrl}#home`);
  await capture('home-390.png', 390);

  await page.getByRole('button', { name: '计划', exact: true }).click();
  await assertNeutralBackground('plan-screen');
  await capture('plan-390.png', 390);

  await page.getByRole('button', { name: '首页', exact: true }).click();
  await page.getByTestId('home-primary-action').click();
  await page.getByTestId('start-workout').click();
  await capture('training-390.png', 390);

  await page.getByTestId('complete-set').click();
  assert.equal(await page.getByTestId('rest-panel').isVisible(), true);
  const activeProgress = Number.parseInt(await page.getByTestId('set-progress').innerText(), 10);
  assert.ok(activeProgress > 0, `active workout progress was ${activeProgress}`);
  await page.getByTestId('skip-rest').click();
  await page.getByTestId('end-workout').click();
  await page.getByTestId('confirm-end-workout').click();
  assert.equal(await page.getByTestId('bottom-nav').count(), 0);
  const completedGroups = Number.parseInt(await page.locator('.summary-stat').filter({ hasText: '完成组数' }).locator('strong').innerText(), 10);
  assert.ok(completedGroups > 0, `summary completed groups was ${completedGroups}`);
  await capture('workout-summary-390.png', 390);

  await page.goto(prototypeUrl);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.goto(`${prototypeUrl}#food`);
  await assertNeutralBackground('food-screen');
  await capture('food-390.png', 390);

  await page.goto(`${prototypeUrl}#profile`);
  await assertNeutralBackground('profile-screen');
  assert.match(await page.locator('.profile-metric').first().locator('strong').innerText(), /^0 \/ 3$/);
  await capture('profile-390.png', 390);

  await page.setViewportSize({ width: 430, height: 844 });
  await page.goto(prototypeUrl);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.goto(`${prototypeUrl}#home`);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth), 0);
  await capture('home-430.png', 430);

  assert.deepEqual(captured.sort(), [
    'food-390.png',
    'home-390.png',
    'home-430.png',
    'plan-390.png',
    'profile-390.png',
    'training-390.png',
    'workout-summary-390.png',
  ]);
});
