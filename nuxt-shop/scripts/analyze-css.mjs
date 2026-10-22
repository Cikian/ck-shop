// scripts/analyze-css.mjs
//
// 店铺端 CSS 体积分析与预算守卫（清单 S22）。
//
// 背景：入口 CSS 一度达到 242 KB（gzip 约 34 KB），其中绝大部分来自
// `@import "@nuxt/ui"` 与 Tailwind 扫描产物。没有度量就只能"感觉有点大"，
// 也无法在 CI 里拦住"某个改动顺手多带进来 50 KB"的回归。
//
// 用法：
//   node scripts/analyze-css.mjs            # 只分析
//   CSS_BUDGET_KB=240 node scripts/analyze-css.mjs   # 超预算时退出码 1
//
// 前置：先 `nuxt build`，本脚本读 .output/public/_nuxt/*.css。

import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { gzipSync } from 'node:zlib'

const CSS_DIR = '.output/public/_nuxt'
const DEFAULT_BUDGET_KB = 240

function kb(bytes) {
  return `${(bytes / 1024).toFixed(1)} KB`
}

function collectCss(dir) {
  const out = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) {
      out.push(...collectCss(full))
    } else if (entry.name.endsWith('.css')) {
      out.push(full)
    }
  }
  return out
}

let files
try {
  files = collectCss(CSS_DIR)
} catch {
  console.error(`找不到 ${CSS_DIR}，请先执行 nuxt build`)
  process.exit(2)
}

if (!files.length) {
  console.error(`${CSS_DIR} 下没有 CSS 产物`)
  process.exit(2)
}

const rows = files
  .map((file) => {
    const raw = readFileSync(file)
    return {
      name: file.split(/[\\/]/).pop(),
      raw: raw.length,
      gzip: gzipSync(raw).length,
      text: raw.toString('utf8'),
    }
  })
  .sort((a, b) => b.raw - a.raw)

const totalRaw = rows.reduce((sum, row) => sum + row.raw, 0)
const totalGzip = rows.reduce((sum, row) => sum + row.gzip, 0)

console.log('CSS 产物（按原始体积排序）')
for (const row of rows.slice(0, 8)) {
  console.log(`  ${row.name.padEnd(34)} ${kb(row.raw).padStart(10)}  gzip ${kb(row.gzip).padStart(9)}`)
}
console.log(`  ${'合计'.padEnd(34)} ${kb(totalRaw).padStart(10)}  gzip ${kb(totalGzip).padStart(9)}`)

// 入口 CSS 的构成：按"选择器首段"粗略聚合，帮助判断体积来自哪里
const entry = rows[0]
const prefixBytes = new Map()
const rulePattern = /([^{}]+)\{[^{}]*\}/g
let match
while ((match = rulePattern.exec(entry.text)) !== null) {
  const selector = match[1].trim()
  if (!selector || selector.startsWith('@')) continue
  const first = selector.split(/[\s,>:+~]/)[0] || selector
  prefixBytes.set(first, (prefixBytes.get(first) ?? 0) + match[0].length)
}

const top = [...prefixBytes.entries()].sort((a, b) => b[1] - a[1]).slice(0, 10)
console.log(`\n${entry.name} 占比最高的选择器（按首段聚合）`)
for (const [prefix, bytes] of top) {
  const percent = ((bytes / entry.raw) * 100).toFixed(1)
  console.log(`  ${prefix.padEnd(30)} ${kb(bytes).padStart(10)}  ${percent}%`)
}

const budgetKb = Number(process.env.CSS_BUDGET_KB || DEFAULT_BUDGET_KB)
const totalKb = totalRaw / 1024
console.log(`\n预算：${budgetKb} KB（可用 CSS_BUDGET_KB 覆盖）  实际：${totalKb.toFixed(1)} KB`)
if (totalKb > budgetKb) {
  console.error(`✗ CSS 体积超出预算 ${(totalKb - budgetKb).toFixed(1)} KB`)
  process.exit(1)
}
console.log('✓ CSS 体积在预算内')
