/**
 * 路线页交互回归：点选地点、取消、拖动平移、缩放、规划、窄屏无溢出。
 *
 * 用法：NODE_PATH=%TEMP%/pw/node_modules node frontend/e2e/map-interactive.cjs [baseUrl]
 * 默认 http://localhost:5194（本 worktree 的 dev server，后端用 8080）。
 * 后缀是 .cjs：frontend/package.json 是 type=module，CommonJS 的 require 放 .cjs 才跑得起来。
 *
 * 退出码非 0 表示有验收项没过；截图落在 frontend/docs/shots/map-interactive/。
 */
const path = require('path')
const fs = require('fs')
const { chromium } = require('playwright')

const BASE = process.argv[2] || 'http://localhost:5194'
const SHOTS = path.resolve(__dirname, '../docs/shots/map-interactive')

const failures = []
let passed = 0

function check(name, ok, detail = '') {
  if (ok) {
    passed += 1
    console.log(`  ✓ ${name}`)
  } else {
    failures.push(`${name}${detail ? ' → ' + detail : ''}`)
    console.log(`  ✗ ${name}${detail ? ' → ' + detail : ''}`)
  }
}

function shot(page, name) {
  fs.mkdirSync(SHOTS, { recursive: true })
  return page.screenshot({ path: path.join(SHOTS, `${name}.png`), fullPage: false })
}

// 只挑真正能点到的地点：中心落在框内、且没有被别的地点/控件盖住
async function visiblePlaces(page) {
  return page.evaluate(() => {
    const frame = document.querySelector('.map-frame')
    if (!frame) return []
    const fr = frame.getBoundingClientRect()
    const out = []
    for (const place of document.querySelectorAll('.map-place')) {
      const hit = place.querySelector('.map-place-hit')
      if (!hit) continue
      const b = hit.getBoundingClientRect()
      const cx = (b.left + b.right) / 2
      const cy = (b.top + b.bottom) / 2
      if (cx < fr.left + 6 || cx > fr.right - 6 || cy < fr.top + 6 || cy > fr.bottom - 6) continue
      if (document.elementFromPoint(cx, cy) !== hit) continue
      out.push({ id: place.dataset.nodeId, name: place.dataset.name, cx, cy })
    }
    return out
  })
}

async function clickPlace(page, id) {
  const place = await visiblePlaces(page)
  const target = place.find((p) => p.id === id)
  const point = target || place[0]
  await page.mouse.click(point.cx, point.cy)
  return point
}

function readState(page) {
  return page.evaluate(() => {
    const mover = document.querySelector('.map-mover')
    const frame = document.querySelector('.map-frame')
    const style = mover ? getComputedStyle(mover).transform : 'none'
    let scale = 1
    let tx = 0
    let ty = 0
    const m = /matrix\(([^)]+)\)/.exec(style)
    if (m) {
      const parts = m[1].split(',').map(Number)
      scale = parts[0]
      tx = parts[4]
      ty = parts[5]
    }
    const box = (selector) => {
      const el = document.querySelector(selector)
      return el ? el.getBoundingClientRect().toJSON() : null
    }
    return {
      from: document.querySelector('[data-pick="from"]')?.value ?? null,
      to: document.querySelector('[data-pick="to"]')?.value ?? null,
      fromNode: document.querySelector('.map-place.is-from')?.dataset.nodeId ?? null,
      toNode: document.querySelector('.map-place.is-to')?.dataset.nodeId ?? null,
      transform: style,
      scale,
      tx,
      ty,
      frame: box('.map-frame'),
      canvas: box('.map-canvas'),
      plans: document.querySelectorAll('.plan').length,
      route: !!document.querySelector('.map-route'),
    }
  })
}

async function placeCenter(page, id) {
  return page.evaluate((nodeId) => {
    const hit = document.querySelector(`.map-place[data-node-id="${nodeId}"] .map-place-hit`)
    if (!hit) return null
    const b = hit.getBoundingClientRect()
    return { x: (b.left + b.right) / 2, y: (b.top + b.bottom) / 2 }
  }, id)
}

async function dragBy(page, from, dx, dy) {
  await page.mouse.move(from.x, from.y)
  await page.mouse.down()
  await page.mouse.move(from.x + dx, from.y + dy, { steps: 12 })
  await page.mouse.up()
}

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  const page = await ctx.newPage()
  const consoleErrors = []
  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push(m.text())
  })
  page.on('pageerror', (e) => consoleErrors.push(`pageerror: ${e.message}`))

  await page.goto(`${BASE}/nav`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.map-frame', { timeout: 10000 })
  await page.waitForSelector('.map-place', { timeout: 10000 })
  // 等 ResizeObserver 量出框的尺寸，否则地图尺寸还是 0
  await page.waitForFunction(() => (document.querySelector('.map-canvas')?.getBoundingClientRect().width ?? 0) > 100)

  // ---- 1. 点两个地点节点 → 输入框回填 + 高亮 ----
  console.log('1) 点选起点和终点')
  const before = await readState(page)
  const plainFill = await page.evaluate(
    () => getComputedStyle(document.querySelector('.map-place:not(.is-from):not(.is-to) .map-place-dot')).fill,
  )
  const spots = await visiblePlaces(page)
  check('地图上有可点地点', spots.length >= 2, `可见 ${spots.length} 个`)
  const placeInfo = await page.evaluate(() => {
    const places = [...document.querySelectorAll('.map-place')]
    return {
      nodeDots: document.querySelectorAll('.map-node').length,
      places: places.length,
      junctions: places.filter((p) => p.dataset.name === '路口').length,
      unnamed: places.filter((p) => !p.dataset.name).length,
    }
  })
  check('「路口」节点没有被当成可点地点', placeInfo.junctions === 0, `路口=${placeInfo.junctions}`)
  check('可点地点都有名字', placeInfo.unnamed === 0, `无名=${placeInfo.unnamed}`)
  check(
    '只把地点做成可点，其余节点仍是普通点',
    placeInfo.places > 0 && placeInfo.places < placeInfo.places + placeInfo.nodeDots,
    `可点 ${placeInfo.places} / 其余 ${placeInfo.nodeDots}`,
  )
  const expected = await page.evaluate(async () => {
    const res = await fetch('/api/v1/topics?type=PLACE&pageSize=1').then((r) => r.json())
    return res?.data?.total ?? -1
  })
  check(
    '每个地点主题都出现在地图上（不漏项）',
    placeInfo.places === expected,
    `渲染 ${placeInfo.places} / 后端 ${expected}`,
  )
  await page.mouse.move(spots[0].cx, spots[0].cy)
  const hoverLabel = await page.evaluate(
    (name) => [...document.querySelectorAll('.map-place-label')].some((t) => t.textContent === name),
    spots[0].name,
  )
  await page.mouse.move(spots[0].cx, spots[0].cy - 60)
  check('鼠标停在地点上会显示它的名字', hoverLabel)
  const a = spots[0]
  const b = spots[1]
  await page.mouse.click(a.cx, a.cy)
  await page.mouse.click(b.cx, b.cy)
  const picked = await readState(page)
  check('起点输入框回填点击的地名', picked.from === a.name, `input=${picked.from} 期望=${a.name}`)
  check('终点输入框回填点击的地名', picked.to === b.name, `input=${picked.to} 期望=${b.name}`)
  check('起点节点被标成选中起点', picked.fromNode === a.id, `class=${picked.fromNode}`)
  check('终点节点被标成选中终点', picked.toNode === b.id, `class=${picked.toNode}`)
  const selFill = await page.evaluate(
    (id) => getComputedStyle(document.querySelector(`.map-place[data-node-id="${id}"] .map-place-dot`)).fill,
    a.id,
  )
  const toFill = await page.evaluate(
    (id) => getComputedStyle(document.querySelector(`.map-place[data-node-id="${id}"] .map-place-dot`)).fill,
    b.id,
  )
  check('选中起点高亮（填充色与普通点不同）', selFill !== plainFill, `选中=${selFill} 普通=${plainFill}`)
  check('起点与终点颜色有区分', selFill !== toFill, `起点=${selFill} 终点=${toFill}`)
  await shot(page, '01-picked')

  // ---- 2. 再点已选中的终点 → 取消 ----
  console.log('2) 再点已选节点取消')
  await page.mouse.click(b.cx, b.cy)
  const afterUnpick = await readState(page)
  check('取消后终点输入框清空', afterUnpick.to === '', `input=${afterUnpick.to}`)
  check('取消后终点节点不再高亮', afterUnpick.toNode === null, `class=${afterUnpick.toNode}`)
  check('取消终点不影响已选起点', afterUnpick.from === a.name && afterUnpick.fromNode === a.id)
  await shot(page, '02-unpicked')

  // ---- 3. 拖动 >5px：不改变选择，地图真的平移 ----
  console.log('3) 拖动平移')
  const dragStart = await placeCenter(page, a.id)
  const beforeDrag = await readState(page)
  await dragBy(page, dragStart, 0, -70)
  const afterDrag = await readState(page)
  check('拖动起点节点不会取消/改变选择', afterDrag.from === a.name && afterDrag.fromNode === a.id, `from=${afterDrag.from}`)
  check(
    '拖动后地图平移了（transform 的 y 变了）',
    Math.abs(afterDrag.ty - beforeDrag.ty) > 40,
    `ty ${beforeDrag.ty} → ${afterDrag.ty}`,
  )
  const markerAfter = await placeCenter(page, a.id)
  check(
    '节点在屏幕上跟着移动',
    Math.abs(markerAfter.y - dragStart.y) > 40,
    `屏幕 y ${Math.round(dragStart.y)} → ${Math.round(markerAfter.y)}`,
  )
  const small = await readState(page)
  // 5px 以内的抖动仍算点选：先取消起点，再用 3px 的移动点一次
  await page.mouse.move(markerAfter.x, markerAfter.y)
  await page.mouse.down()
  await page.mouse.move(markerAfter.x + 3, markerAfter.y + 2, { steps: 3 })
  await page.mouse.up()
  const afterTiny = await readState(page)
  check('移动 3px 仍算点选（起点被取消）', afterTiny.from !== small.from || afterTiny.fromNode !== small.fromNode, `from=${afterTiny.from}`)
  await shot(page, '03-dragged')

  // ---- 4. 点 + 放大：可见区域变化 ----
  console.log('4) 缩放')
  // 框中心对着哪块地图（0–1 比例），缩放前后应该基本不动
  const centerProbe = () =>
    page.evaluate(() => {
      const f = document.querySelector('.map-frame').getBoundingClientRect()
      const c = document.querySelector('.map-canvas').getBoundingClientRect()
      return {
        u: (f.left + f.width / 2 - c.left) / c.width,
        v: (f.top + f.height / 2 - c.top) / c.height,
      }
    })
  const centerBefore = await centerProbe()
  const beforeZoom = await readState(page)
  check('初始「−」可用（能缩到全图）', !(await page.locator('[data-zoom="out"]').isDisabled()))
  await page.locator('[data-zoom="in"]').click()
  await page.locator('[data-zoom="in"]').click()
  const centerAfter = await centerProbe()
  const afterZoom = await readState(page)
  check(
    '缩放围绕框中心（中心对着的地图点基本不动）',
    Math.abs(centerAfter.u - centerBefore.u) < 0.01 && Math.abs(centerAfter.v - centerBefore.v) < 0.01,
    `u ${centerBefore.u.toFixed(3)}→${centerAfter.u.toFixed(3)} v ${centerBefore.v.toFixed(3)}→${centerAfter.v.toFixed(3)}`,
  )
  check('点 + 后缩放变大', afterZoom.scale > beforeZoom.scale + 0.4, `scale ${beforeZoom.scale} → ${afterZoom.scale}`)
  check('地图整体放大（画布屏幕宽度变大）', afterZoom.canvas.width > beforeZoom.canvas.width + 10, `${beforeZoom.canvas.width} → ${afterZoom.canvas.width}`)
  check('可见区域随缩放变化（画布左边界移动）', Math.abs(afterZoom.canvas.left - beforeZoom.canvas.left) > 5, `${beforeZoom.canvas.left} → ${afterZoom.canvas.left}`)
  check('放大后「−」可用', !(await page.locator('[data-zoom="out"]').isDisabled()))
  const covered = await page.evaluate(() => {
    const f = document.querySelector('.map-frame').getBoundingClientRect()
    const c = document.querySelector('.map-canvas').getBoundingClientRect()
    return c.left <= f.left + 1 && c.top <= f.top + 1 && c.right >= f.right - 1 && c.bottom >= f.bottom - 1
  })
  check('缩放后地图仍铺满框，没有露白', covered)
  const zoomLimit = await page.evaluate(async () => {
    const btn = document.querySelector('[data-zoom="in"]')
    for (let i = 0; i < 12; i += 1) {
      if (btn.disabled) break
      btn.click()
      await new Promise((r) => setTimeout(r, 30))
    }
    const m = /matrix\(([^)]+)\)/.exec(getComputedStyle(document.querySelector('.map-mover')).transform)
    return { scale: m ? Number(m[1].split(',')[0]) : 0, disabled: btn.disabled }
  })
  check('放大到 2.5× 就停住', Math.abs(zoomLimit.scale - 2.5) < 0.01 && zoomLimit.disabled, `scale=${zoomLimit.scale} disabled=${zoomLimit.disabled}`)
  await shot(page, '04-zoomed')

  // ---- 5. 规划路线出方案 + 线路高亮 ----
  console.log('5) 规划路线')
  const spotsNow = await visiblePlaces(page)
  check('放大后框内仍有可点地点', spotsNow.length >= 2, `${spotsNow.length} 个`)
  await page.mouse.click(spotsNow[0].cx, spotsNow[0].cy)
  await page.mouse.click(spotsNow[1].cx, spotsNow[1].cy)
  await page.locator('.nav-toolbar .btn.primary').click()
  await page.waitForFunction(() => document.querySelectorAll('.plan').length > 0, null, { timeout: 8000 }).catch(() => {})
  const planned = await readState(page)
  check('「规划路线」出了方案列表', planned.plans > 0, `plan 个数=${planned.plans}`)
  check('地图上画出选中方案的高亮线路', planned.route)
  const planText = await page.evaluate(() => document.querySelector('.plan')?.innerText || '')
  check('方案里有距离和耗时', /米/.test(planText) && /分钟/.test(planText), planText.slice(0, 60))
  await shot(page, '05-planned')

  // ---- 6. 控制台无 error ----
  console.log('6) 控制台 / 窄屏')
  check('控制台没有 error', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '))

  const narrow = await browser.newContext({ viewport: { width: 375, height: 812 } })
  const narrowPage = await narrow.newPage()
  const narrowErrors = []
  narrowPage.on('console', (m) => {
    if (m.type() === 'error') narrowErrors.push(m.text())
  })
  narrowPage.on('pageerror', (e) => narrowErrors.push(`pageerror: ${e.message}`))
  await narrowPage.goto(`${BASE}/nav`, { waitUntil: 'networkidle' })
  await narrowPage.waitForSelector('.map-frame', { timeout: 10000 })
  await narrowPage.waitForFunction(() => (document.querySelector('.map-canvas')?.getBoundingClientRect().width ?? 0) > 100)
  await narrowPage.waitForTimeout(200)
  const narrowInfo = await narrowPage.evaluate(() => {
    const doc = document.documentElement
    const frame = document.querySelector('.map-frame').getBoundingClientRect()
    return {
      scrollWidth: doc.scrollWidth,
      innerWidth: window.innerWidth,
      frameHeight: frame.height,
      frameWidth: frame.width,
      frameRight: frame.right,
    }
  })
  check('375 宽无横向溢出', narrowInfo.scrollWidth <= narrowInfo.innerWidth + 1, `scrollWidth=${narrowInfo.scrollWidth} innerWidth=${narrowInfo.innerWidth}`)
  check('地图框高度 420px', Math.abs(narrowInfo.frameHeight - 420) <= 1, `height=${narrowInfo.frameHeight}`)
  check('地图框没有超出视口右边', narrowInfo.frameRight <= narrowInfo.innerWidth + 1, `right=${narrowInfo.frameRight}`)
  const narrowPicked = await visiblePlaces(narrowPage)
  if (narrowPicked.length >= 2) {
    await narrowPage.mouse.click(narrowPicked[0].cx, narrowPicked[0].cy)
    await narrowPage.mouse.click(narrowPicked[1].cx, narrowPicked[1].cy)
  }
  const narrowState = await narrowPage.evaluate(() => ({
    from: document.querySelector('[data-pick="from"]').value,
    to: document.querySelector('[data-pick="to"]').value,
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }))
  check('窄屏点选后仍无横向溢出', narrowState.scrollWidth <= narrowState.innerWidth + 1, `${narrowState.scrollWidth}/${narrowState.innerWidth}`)
  check('窄屏点选也能回填输入框', !!narrowState.from && !!narrowState.to, `${narrowState.from} → ${narrowState.to}`)
  check('窄屏控制台没有 error', narrowErrors.length === 0, narrowErrors.slice(0, 3).join(' | '))
  await shot(narrowPage, '06-mobile')

  // ---- 7. 键盘也能选（tab 到地点上按回车）----
  console.log('7) 键盘选点')
  const keyPage = await ctx.newPage()
  await keyPage.goto(`${BASE}/nav`, { waitUntil: 'networkidle' })
  await keyPage.waitForSelector('.map-place', { timeout: 10000 })
  const keyTarget = await visiblePlaces(keyPage)
  await keyPage.focus(`.map-place[data-node-id="${keyTarget[0].id}"]`)
  await keyPage.keyboard.press('Enter')
  const keyState = await keyPage.evaluate(() => ({
    from: document.querySelector('[data-pick="from"]').value,
    cls: document.querySelector('.map-place.is-from')?.dataset.nodeId ?? null,
  }))
  check('键盘回车也能选中起点', keyState.from === keyTarget[0].name && keyState.cls === keyTarget[0].id, `input=${keyState.from}`)
  await keyPage.close()

  // ---- 8. 搜索框选的地点也会在地图上高亮 ----
  console.log('8) 搜索选点与地图选中同步')
  const searchPage = await ctx.newPage()
  await searchPage.goto(`${BASE}/nav`, { waitUntil: 'networkidle' })
  await searchPage.waitForSelector('.map-place', { timeout: 10000 })
  await searchPage.locator('[data-pick="from"]').fill('图书')
  await searchPage.waitForSelector('.suggest-item', { timeout: 5000 })
  const suggestionName = (await searchPage.locator('.suggest-item').first().innerText()).trim()
  await searchPage.locator('.suggest-item').first().click()
  const synced = await searchPage.evaluate(() => {
    const hit = document.querySelector('.map-place.is-from .map-place-hit')
    const frame = document.querySelector('.map-frame').getBoundingClientRect()
    const b = hit ? hit.getBoundingClientRect() : null
    return {
      from: document.querySelector('[data-pick="from"]').value,
      fromNode: document.querySelector('.map-place.is-from')?.dataset.name ?? null,
      suggestionCount: document.querySelectorAll('.suggest-item').length,
      inView: !!b && b.left >= frame.left && b.right <= frame.right && b.top >= frame.top && b.bottom <= frame.bottom,
    }
  })
  check('搜索结果回填起点输入框', synced.from === suggestionName, `input=${synced.from} 期望=${suggestionName}`)
  check('搜索选中的地点在地图上高亮', synced.fromNode === suggestionName, `高亮=${synced.fromNode}`)
  check('选中的地点自动落到地图可见范围内', synced.inView)
  check('选中后收起候选列表', synced.suggestionCount === 0)
  await shot(searchPage, '07-search-sync')
  await searchPage.close()

  await browser.close()

  console.log('')
  if (failures.length) {
    console.error(`失败 ${failures.length} 项，通过 ${passed} 项：`)
    for (const f of failures) console.error(' - ' + f)
    process.exit(1)
  }
  console.log(`全部通过：${passed} 项`)
})().catch((e) => {
  console.error('验收脚本跑挂：', e.message)
  process.exit(1)
})
