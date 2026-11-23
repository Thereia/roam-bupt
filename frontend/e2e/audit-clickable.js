const { chromium } = require('playwright')
const fs = require('fs')
const APP = 'http://192.168.153.129:18081'
const OUT = 'E:/code/roam-bupt/frontend/docs/shots/audit'
fs.mkdirSync(OUT, { recursive: true })

const pages = [
  ['/', 'home'],
  ['/topics', 'topics'],
  ['/topics/79', 'topic'],
  ['/posts/23', 'post'],
  ['/nav', 'nav'],
  ['/search?q=%E9%A3%9F%E5%A0%82', 'search'],
  ['/publish', 'publish'],
  ['/collections', 'collections'],
  ['/notifications', 'notifications'],
  ['/users/1', 'user'],
]

async function audit(page, name) {
  return await page.evaluate((pageName) => {
    const visible = (el) => {
      const r = el.getBoundingClientRect()
      const cs = getComputedStyle(el)
      return r.width > 0 && r.height > 0 && cs.visibility !== 'hidden' && cs.display !== 'none'
    }
    const items = []
    for (const el of document.querySelectorAll('button, a, [role="button"]')) {
      if (!visible(el)) continue
      const text = (el.innerText || el.value || '').trim().replace(/\s+/g, ' ').slice(0, 24)
      if (!text) continue
      const disabled = el.disabled === true || el.getAttribute('aria-disabled') === 'true'
      const href = el.tagName === 'A' ? el.getAttribute('href') : null
      const title = el.getAttribute('title') || ''
      const cls = (el.className && String(el.className).slice(0, 30)) || ''
      let kind = 'ok'
      if (disabled) kind = 'disabled'
      else if (el.tagName === 'A' && (!href || href === '#' || href.startsWith('javascript'))) kind = 'dead-link'
      else if (el.tagName === 'A') kind = 'link'
      items.push({ page: pageName, tag: el.tagName.toLowerCase(), text, kind, href, title, cls })
    }
    // 看起来可点但既不是 a 也不是 button（有 cursor:pointer）
    for (const el of document.querySelectorAll('div, span, li, svg')) {
      if (!visible(el)) continue
      if (getComputedStyle(el).cursor !== 'pointer') continue
      if (el.closest('a, button, [role="button"]')) continue
      items.push({
        page: pageName, tag: el.tagName.toLowerCase(),
        text: (el.innerText || '').trim().replace(/\s+/g, ' ').slice(0, 24),
        kind: 'pointer-only', href: null, title: el.getAttribute('title') || '',
        cls: String(el.className).slice(0, 30),
      })
    }
    return items
  }, name)
}

;(async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await b.newContext({ viewport: { width: 1280, height: 1000 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()) })

  await page.goto(APP + '/login')
  await page.fill('input[type="text"], input:not([type])', 'seed_lin')
  await page.fill('input[type="password"]', 'secret123')
  await page.click('button')
  await page.waitForURL(APP + '/')

  const all = []
  for (const [path, name] of pages) {
    await page.goto(APP + path)
    await page.waitForTimeout(1200)
    all.push(...await audit(page, name))
    await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: false })
  }
  // 管理后台
  await page.goto(APP + '/admin/posts')
  await page.waitForTimeout(1500)
  all.push(...await audit(page, 'admin'))

  const byKind = {}
  for (const item of all) byKind[item.kind] = (byKind[item.kind] || 0) + 1

  fs.writeFileSync(OUT + '/audit.json', JSON.stringify(all, null, 1))
  const interesting = all.filter((i) => i.kind !== 'link' && i.kind !== 'ok')
  console.log(JSON.stringify({ counts: byKind, errors: errs }))
  console.log('--- 需要看的 ---')
  for (const i of interesting) console.log(`${i.page} [${i.kind}] <${i.tag}> "${i.text}" title="${i.title}" class="${i.cls}"`)
  await b.close()
})().catch((e) => { console.error('AUDIT FAIL', e.message); process.exit(1) })
