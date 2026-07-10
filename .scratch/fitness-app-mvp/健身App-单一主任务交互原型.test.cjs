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
