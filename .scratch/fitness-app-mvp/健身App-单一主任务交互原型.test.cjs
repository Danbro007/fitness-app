const { test, before, beforeEach, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { chromium } = require('playwright');

const prototypeUrl = 'http://127.0.0.1:8087/.scratch/fitness-app-mvp/%E5%81%A5%E8%BA%ABApp-%E5%8D%95%E4%B8%80%E4%B8%BB%E4%BB%BB%E5%8A%A1%E4%BA%A4%E4%BA%92%E5%8E%9F%E5%9E%8B.html';
let browser;
let page;

before(async () => {
  browser = await chromium.launch({
    headless: true,
    executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  });
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
