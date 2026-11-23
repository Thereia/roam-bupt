/**
 * 图片灯箱 + 卡片图贴合的浏览器验收。
 *
 * 用真实后端数据（主目录 8080）验证：卡片点图开灯箱且不跳帖子、Esc 关闭；
 * 用「网络层注入的帖子数据」验证：宽图/竖图卡片框贴合、详情页多图画廊与切换。
 * 数据注入只替换 /api/v1/posts 与 /api/v1/posts/:id 的响应，组件、路由、DOM、事件都是真的；
 * 图片用 data:image/svg+xml，避免往主目录的 uploads 里写文件。
 *
 * 用法：node image-lightbox-check.js [baseUrl] [outDir]
 */
const { chromium } = require('playwright')
const fs = require('fs')

const BASE = process.argv[2] || 'http://localhost:5193'
const OUT = process.argv[3] || 'E:/code/roam-bupt/frontend/docs/shots/image-lightbox'
fs.mkdirSync(OUT, { recursive: true })

const svg = (w, h, fill, label) =>
  'data:image/svg+xml;charset=utf-8,' +
  encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}"><rect width="${w}" height="${h}" fill="${fill}"/><text x="50%" y="50%" fill="#fff" font-size="${Math.round(w / 12)}" text-anchor="middle" dominant-baseline="middle">${label}</text></svg>`,
  )

const WIDE = svg(1200, 600, '#2f5d50', 'wide 1200x600')
const TALL = svg(400, 800, '#8a5a2b', 'tall 400x800')
const SQUARE = svg(600, 600, '#4a4f7a', 'square 600x600')

const post = (id, title, media) => ({
  id,
  title,
  excerpt: '验收用帖子',
  content: '验收用正文。\n第二段：检查详情页画廊。',
  status: 'ONLINE',
  author: { id: 78, nickname: '验收员', avatar: null, followed: null },
  topics: [{ id: 109, type: 'PLACE', name: '瑞幸', sortNo: 0 }],
  media: media.map((url, i) => ({ type: 'IMAGE', url, sortNo: i })),
  counts: { like: 0, collect: 0, comment: 0, view: 0 },
  myLiked: null,
  myCollected: null,
  createdTime: '2026-11-23T17:49:04',
  updatedTime: '2026-11-23T17:49:04',
})

const problems = []
const consoleErrors = []
function check(name, ok, detail) {
  if (ok) console.log(`  ✔ ${name}`)
  else {
    console.log(`  ✘ ${name}${detail ? ' — ' + detail : ''}`)
    problems.push(`${name}${detail ? ' — ' + detail : ''}`)
  }
}

const attrs = (page, sel) =>
  page.evaluate((s) => {
    const el = document.querySelector(s)
    if (!el) return null
    const r = el.getBoundingClientRect()
    return { w: r.width, h: r.height, left: r.left, right: r.right, bottom: r.bottom }
  }, sel)

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const watch = (page) => {
    page.on('console', (m) => {
      if (m.type() === 'error') consoleErrors.push(`${page.url()} → ${m.text()}`)
    })
    page.on('pageerror', (e) => consoleErrors.push(`${page.url()} → ${e.message}`))
  }

  // ---------- 1) 真实数据：卡片点图开灯箱，URL 不变 ----------
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 1000 } })
  const page = await ctx.newPage()
  watch(page)
  // 固定注入一条单图帖，避免依赖开发库里恰好有几张图
  await page.route('**/api/v1/posts?*', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        message: 'ok',
        data: { total: 1, page: 1, pageSize: 10, list: [post(9100, '单图卡片', [WIDE])] },
      }),
    })
  })
  await page.goto(BASE + '/', { waitUntil: 'networkidle' })
  await page.waitForSelector('.post .thumb img', { timeout: 15000 })
  await page.waitForFunction(() => {
    const img = document.querySelector('.post .thumb img')
    return img && img.complete && img.naturalWidth > 0
  })

  const beforeUrl = page.url()
  const cardThumb = await attrs(page, '.post .thumb')
  await page.screenshot({ path: `${OUT}/01-home-cards-real.png` })

  await page.locator('.post .thumb img').first().click()
  await page.waitForSelector('.lightbox', { state: 'visible', timeout: 5000 })
  check('卡片点图打开灯箱', true)
  check('卡片点图不跳帖子（URL 不变）', page.url() === beforeUrl, `before=${beforeUrl} after=${page.url()}`)
  check('单图不显示计数', (await page.locator('.lightbox-count').count()) === 0)
  check('打开时锁 body 滚动', (await page.evaluate(() => getComputedStyle(document.body).overflow)) === 'hidden')
  await page.screenshot({ path: `${OUT}/02-card-lightbox.png` })
  await page.keyboard.press('Escape')
  await page.waitForSelector('.lightbox', { state: 'detached', timeout: 5000 })
  check('Esc 关闭灯箱', true)
  check('关闭后恢复 body 滚动', (await page.evaluate(() => getComputedStyle(document.body).overflow)) !== 'hidden')

  // ---------- 2) 注入数据：卡片框贴合宽图/竖图 ----------
  const postsCtx = await browser.newContext({ viewport: { width: 1280, height: 1000 } })
  const cards = await postsCtx.newPage()
  watch(cards)
  await cards.route('**/api/v1/posts?*', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        message: 'ok',
        data: {
          total: 2,
          page: 1,
          pageSize: 10,
          list: [post(9001, '宽图卡片', [WIDE]), post(9002, '竖图卡片', [TALL])],
        },
      }),
    })
  })
  await cards.goto(BASE + '/', { waitUntil: 'networkidle' })
  await cards.waitForFunction(
    () =>
      [...document.querySelectorAll('.post .thumb img')].length === 2 &&
      [...document.querySelectorAll('.post .thumb img')].every((i) => i.complete && i.naturalWidth > 0),
    null,
    { timeout: 15000 },
  )
  await cards.screenshot({ path: `${OUT}/03-home-cards-fit.png` })

  const fit = await cards.evaluate(() =>
    [...document.querySelectorAll('.post')]
      .map((card) => {
        const box = card.querySelector('.thumb')
        const img = box && box.querySelector('img')
        if (!img) return null
        const b = box.getBoundingClientRect()
        const i = img.getBoundingClientRect()
        return {
          title: card.querySelector('h2')?.textContent?.trim(),
          natural: `${img.naturalWidth}x${img.naturalHeight}`,
          box: { w: b.width, h: b.height },
          img: { w: i.width, h: i.height },
        }
      })
      .filter(Boolean),
  )
  for (const f of fit) {
    const ratioDiff = Math.abs(f.box.w / f.box.h - f.img.w / f.img.h)
    const fits = f.img.w <= f.box.w + 1 && f.img.h <= f.box.h + 1
    const hug = f.box.w <= f.img.w + 2 && f.box.h <= f.img.h + 2
    check(`「${f.title}」图片完整显示（${f.natural}）`, fits, `box=${f.box.w}x${f.box.h} img=${f.img.w}x${f.img.h}`)
    check(`「${f.title}」容器贴合图片（宽高比误差 ${ratioDiff.toFixed(4)} < 0.05）`, ratioDiff < 0.05)
    check(`「${f.title}」容器不大于图片 + 2px`, hug, `box=${f.box.w}x${f.box.h} img=${f.img.w}x${f.img.h}`)
  }
  check('宽图/竖图两张卡片都取到', fit.length === 2)

  // ---------- 3) 注入数据：详情页两张图，灯箱计数/切换/关闭 ----------
  const detailCtx = await browser.newContext({ viewport: { width: 1280, height: 1000 } })
  const detail = await detailCtx.newPage()
  watch(detail)
  const detailPost = post(23, '详情页两张图', [WIDE, TALL])
  await detail.route('**/api/v1/posts/23*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, message: 'ok', data: detailPost }),
    }),
  )
  await detail.goto(BASE + '/posts/23', { waitUntil: 'networkidle' })
  await detail.waitForSelector('.post-gallery .gallery-item img')
  await detail.waitForFunction(() =>
    [...document.querySelectorAll('.post-gallery img')].every((i) => i.complete && i.naturalWidth > 0),
  )
  await detail.screenshot({ path: `${OUT}/04-post-gallery-fit.png` })

  const gallery = await detail.evaluate(() =>
    [...document.querySelectorAll('.post-gallery .gallery-item')].map((item) => {
      const b = item.getBoundingClientRect()
      const i = item.querySelector('img').getBoundingClientRect()
      return { box: { w: b.width, h: b.height }, img: { w: i.width, h: i.height } }
    }),
  )
  for (const [n, g] of gallery.entries()) {
    check(
      `画廊第 ${n + 1} 张贴合且不高于 320px`,
      g.img.w <= g.box.w + 1 && g.img.h <= g.box.h + 1 && g.box.h <= 320 && Math.abs(g.box.w / g.box.h - g.img.w / g.img.h) < 0.05,
      `box=${g.box.w}x${g.box.h} img=${g.img.w}x${g.img.h}`,
    )
  }

  await detail.locator('.post-gallery .gallery-item').first().click()
  await detail.waitForSelector('.lightbox', { state: 'visible' })
  await detail.waitForSelector('.lightbox img')
  check('画廊点图打开灯箱', true)
  check('计数显示 1/2', (await detail.locator('.lightbox-count').innerText()).trim() === '1/2')
  const firstSrc = await detail.locator('.lightbox img').getAttribute('src')
  check('灯箱打开的是点的那张图', firstSrc === WIDE)
  check('灯箱图片完整显示（contain）', (await detail.evaluate(() => {
    const img = document.querySelector('.lightbox img')
    const r = img.getBoundingClientRect()
    return r.width <= window.innerWidth && r.height <= window.innerHeight
  })) === true)
  await detail.screenshot({ path: `${OUT}/05-detail-lightbox-1of2.png` })

  await detail.locator('.lightbox-next').click()
  await detail.waitForTimeout(150)
  check('右箭头切到 2/2', (await detail.locator('.lightbox-count').innerText()).trim() === '2/2')
  const secondSrc = await detail.locator('.lightbox img').getAttribute('src')
  check('切换后是第二张图', secondSrc === TALL)
  await detail.screenshot({ path: `${OUT}/06-detail-lightbox-2of2.png` })

  await detail.keyboard.press('ArrowLeft')
  await detail.waitForTimeout(150)
  check('键盘左键回到 1/2', (await detail.locator('.lightbox-count').innerText()).trim() === '1/2')

  await detail.locator('.lightbox-close').click()
  await detail.waitForSelector('.lightbox', { state: 'detached' })
  check('点 × 关闭灯箱', true)
  check('关闭后可继续与页面交互', (await detail.locator('.post-gallery .gallery-item').count()) === 2)

  await detail.locator('.post-gallery .gallery-item').nth(1).click()
  await detail.waitForSelector('.lightbox', { state: 'visible' })
  check('再次打开定位到第 2 张', (await detail.locator('.lightbox-count').innerText()).trim() === '2/2')
  await detail.keyboard.press('Escape')
  await detail.waitForSelector('.lightbox', { state: 'detached' })
  check('Esc 关闭灯箱', true)

  // 点遮罩关闭（点左边缘，避开图片与按钮）
  await detail.locator('.post-gallery .gallery-item').first().click()
  await detail.waitForSelector('.lightbox', { state: 'visible' })
  await detail.mouse.click(6, 300)
  await detail.waitForSelector('.lightbox', { state: 'detached' })
  check('点遮罩关闭灯箱', true)

  // ---------- 4) 375 宽首页无横向溢出 ----------
  const mobileCtx = await browser.newContext({ viewport: { width: 375, height: 812 } })
  const mobile = await mobileCtx.newPage()
  watch(mobile)
  await mobile.goto(BASE + '/', { waitUntil: 'networkidle' })
  await mobile.waitForSelector('.post .thumb img', { timeout: 15000 }).catch(() => {})
  await mobile.waitForTimeout(600)
  const overflow = await mobile.evaluate(() => ({
    doc: document.documentElement.scrollWidth - window.innerWidth,
    body: document.body.scrollWidth - window.innerWidth,
  }))
  check('375 宽首页无横向溢出', overflow.doc <= 0 && overflow.body <= 0, JSON.stringify(overflow))
  await mobile.screenshot({ path: `${OUT}/07-home-375.png` })

  await browser.close()

  console.log('\n控制台错误：' + (consoleErrors.length ? '\n - ' + consoleErrors.join('\n - ') : '无'))
  if (consoleErrors.length) problems.push(`${consoleErrors.length} 个控制台错误`)

  if (problems.length) {
    console.error(`\nFAILED: ${problems.length} 项`)
    for (const p of problems) console.error(' - ' + p)
    process.exit(1)
  }
  console.log(`\nPASSED: 灯箱与图片贴合验收全部通过，截图在 ${OUT}`)
})().catch((e) => {
  console.error('CHECK FAILED: ' + e.message)
  process.exit(1)
})
