#!/usr/bin/env node
/**
 * 实现清单的一致性校验。
 *
 * 守住三件事：
 * 1. 清单里 ✅ 的行，验收必须写具体（测试类 / 截图 / 手测 / 演练），不能写“已完成”；
 * 2. 清单点名的测试类必须真实存在，防止清单变成假账；
 * 3. 每份域设计都必须被清单引用，且域设计里不允许再写状态（状态只能有一处）。
 */
import { readdirSync, readFileSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const listPath = join(root, 'docs', '实现清单.md')
const problems = []

const list = readFileSync(listPath, 'utf8')
const rows = list
  .split('\n')
  .map((line) => line.trim())
  .filter((line) => /^\|\s*[A-Z]\d+\s*\|/.test(line))
  .map((line) => line.split('|').slice(1, -1).map((cell) => cell.trim()))

if (rows.length === 0) problems.push('实现清单里没有解析到任何任务行')

// 1 + 2：✅ 行的验收必须具体，且测试类要存在
const testFiles = new Set()
const walk = (dir) => {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'target' || entry.name === 'node_modules' || entry.name === '.git') continue
    const full = join(dir, entry.name)
    if (entry.isDirectory()) walk(full)
    else if (entry.name.endsWith('Test.java')) testFiles.add(entry.name.replace('.java', ''))
  }
}
walk(join(root, 'backend'))

const concrete = /Test|\d+\s*条|\.png|手测|演练|浏览器/
for (const row of rows) {
  const [code, , status, , , acceptance] = row
  if (status !== '✅') continue
  if (!acceptance || !concrete.test(acceptance)) {
    problems.push(`${code}: ✅ 但验收写得太虚（"${acceptance || '空'}"）`)
  }
  for (const token of acceptance.match(/[A-Z][A-Za-z]*Test/g) || []) {
    if (!testFiles.has(token)) problems.push(`${code}: 验收点名的 ${token} 在 backend 下不存在`)
  }
}

// 3：域设计必须被引用，且不得再写状态
const designDir = join(root, 'docs')
const designs = readdirSync(designDir).filter((name) => /^域设计-.+\.md$/.test(name))
for (const design of designs) {
  if (!list.includes(design)) problems.push(`docs/${design} 没有被实现清单引用`)
  const body = readFileSync(join(designDir, design), 'utf8')
  if (/^>\s*状态[:：]/m.test(body)) problems.push(`docs/${design} 还在写状态，状态只能留在实现清单`)
}

// 过期残留
for (const file of ['docs/域设计-auth.md', 'docs/域设计-post.md']) {
  const full = join(root, file)
  if (existsSync(full) && readFileSync(full, 'utf8').includes('待确认')) {
    problems.push(`${file} 仍写着「待确认」，与清单状态冲突`)
  }
}

if (problems.length) {
  console.error('check-docs 失败：')
  for (const p of problems) console.error(' - ' + p)
  process.exit(1)
}
console.log(`check-docs 通过：${rows.length} 行任务、${designs.length} 份域设计`)
