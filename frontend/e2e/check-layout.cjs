/**
 * 布局回归检查：图片不能撑破容器、页面不能横向溢出。
 *
 * 用法：node frontend/e2e/check-layout.cjs [baseUrl]
 * 默认 http://localhost:5191（本地 dev）。需要本地装 playwright。
 *
 * 退出码非 0 表示发现布局问题；这是交差前的必跑项（见 AGENTS.md）。
 */
const { chromium } = require('playwright')

const BASE = process.argv[2] || 'http://localhost:5191'
const PAGES = ['/', '/topics', '/posts/23', '/nav']

// 不依赖真实数据：塞一张 800x600 的图进缩略图，图片必须被容器框住
async function injectBigImage(page) {
  await page.evaluate(async () => {
    let thumb = document.querySelector('.thumb')
    if (!thumb) {
      const host = document.createElement('article')
      host.className = 'post'
      host.innerHTML = '<div class="thumb"></div>'
      const feed = document.querySelector('main') || document.body
      feed.prepend(host)
      thumb = host.querySelector('.thumb')
    }
    const img = new Image()
    img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(
      '<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600"><rect width="800" height="600" fill="#c33"/></svg>')
    img.setAttribute('data-layout-probe', '1')
    thumb.appendChild(img)
    await new Promise((resolve) => { img.onload = resolve; setTimeout(resolve, 300) })
  })
  await page.waitForTimeout(150)
}

async function collect(page) {
  return page.evaluate(() => {
    const problems = []
    const vw = window.innerWidth
    for (const el of document.querySelectorAll('*')) {
      const box = el.getBoundingClientRect()
      if (box.width === 0 && box.height === 0) continue
      if (box.right > vw + 1) {
        problems.push(`横向溢出 ${Math.round(box.right - vw)}px: <${el.tagName.toLowerCase()} class="${String(el.className).slice(0, 30)}">`)
      }
    }
    for (const thumb of document.querySelectorAll('.thumb')) {
      const tb = thumb.getBoundingClientRect()
      const img = thumb.querySelector('img')
      if (!img) continue
      const ib = img.getBoundingClientRect()
      if (ib.height > tb.height + 1 || ib.width > tb.width + 1) {
        problems.push(`图片撑破缩略图: img ${Math.round(ib.width)}x${Math.round(ib.height)} > box ${Math.round(tb.width)}x${Math.round(tb.height)}`)
      }
      const card = thumb.closest('.post')
      const next = card && card.nextElementSibling
      if (next && tb.bottom > next.getBoundingClientRect().top + 1) {
        problems.push(`缩略图压到下一张卡片: bottom=${Math.round(tb.bottom)} nextTop=${Math.round(next.getBoundingClientRect().top)}`)
      }
    }
    return problems
  })
}

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const problems = []
  for (const width of [1280, 375]) {
    const ctx = await browser.newContext({ viewport: { width, height: 900 } })
    const page = await ctx.newPage()
    page.on('console', (m) => { if (m.type() === 'error') problems.push(`控制台报错 @${width} ${page.url()}: ${m.text()}`) })
    for (const path of PAGES) {
      await page.goto(BASE + path, { waitUntil: 'networkidle' }).catch(() => {})
      await page.waitForTimeout(600)
      if (path === '/') await injectBigImage(page)
      for (const p of await collect(page)) problems.push(`${path} @${width}px → ${p}`)
    }
    await ctx.close()
  }

  await browser.close()

  if (problems.length) {
    console.error(`发现 ${problems.length} 个布局问题：`)
    for (const p of problems) console.error(' - ' + p)
    process.exit(1)
  }
  console.log(`布局检查通过：${PAGES.length} 个页面 × 2 个宽度，无溢出、无图片撑破`)
})().catch((e) => {
  console.error('布局检查失败：', e.message)
  process.exit(1)
})
