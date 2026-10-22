// scripts/check-contrast.mjs
//
// 设计 token 对比度检查（WCAG AA：正文 4.5:1，大字号 3:1）。
//
// 为什么要有这个脚本：对比度问题在开发机的宽屏 + 高亮环境下几乎看不出来，
// 但 12–13px 的灰色正文在真实屏幕上就是"看不清"。靠人眼评审一定会漏，
// 所以把它变成一条可重复执行的命令：`pnpm check:contrast`。
//
// 数据来源：app/assets/css/tailwind.css 的 @theme token（唯一来源，不在这里复制色值）。

import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const cssPath = resolve(here, '../app/assets/css/tailwind.css')
const css = readFileSync(cssPath, 'utf8')

/** 从 @theme 里解析 `--color-xxx: #RRGGBB;`（同名重复时取最后一次定义） */
function readTokens(source) {
  const tokens = new Map()
  const re = /--color-([a-z0-9-]+)\s*:\s*(#[0-9a-fA-F]{3,8})\s*;/g
  let match
  while ((match = re.exec(source)) !== null) {
    tokens.set(match[1], match[2])
  }
  return tokens
}

function expandHex(hex) {
  const value = hex.replace('#', '')
  if (value.length === 3) {
    return value.split('').map(ch => ch + ch).join('')
  }
  return value.slice(0, 6)
}

function relativeLuminance(hex) {
  const rgb = expandHex(hex).match(/.{2}/g).map(pair => parseInt(pair, 16) / 255)
  const [r, g, b] = rgb.map(channel => (channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function contrast(a, b) {
  const la = relativeLuminance(a)
  const lb = relativeLuminance(b)
  const [light, dark] = la > lb ? [la, lb] : [lb, la]
  return (light + 0.05) / (dark + 0.05)
}

const tokens = readTokens(css)

/** 需要满足 AA 的组合：[前景 token, 背景 token, 说明] */
const checks = [
  ['muted', 'surface', '12–13px 辅助正文（白底）'],
  ['muted', 'cream', '12–13px 辅助正文（奶油底）'],
  ['brand-cta', 'surface', 'CTA 按钮上的白字（用 contrast 的反向组合计算）'],
  ['warning-600', 'warning-50', '警告文案'],
  ['success-600', 'success-50', '成功文案'],
  ['error-600', 'error-50', '错误文案'],
  ['ink-soft', 'surface', '次要正文（白底）'],
]

// CTA 是"白字 + 深色底"，单独按前景 #ffffff 计算
const white = '#FFFFFF'
const failures = []
const lines = []

for (const [fg, bg, label] of checks) {
  const fgValue = tokens.get(fg)
  const bgValue = tokens.get(bg)
  if (!fgValue || !bgValue) {
    failures.push(`缺少 token：--color-${fg} / --color-${bg}（${label}）`)
    continue
  }
  const ratio = fg === 'brand-cta'
    ? contrast(white, fgValue)
    : contrast(fgValue, bgValue)
  const pass = ratio >= 4.5
  lines.push(`${pass ? 'PASS' : 'FAIL'}  ${label}  ${fgValue} on ${fg === 'brand-cta' ? white : bgValue}  ${ratio.toFixed(2)}:1`)
  if (!pass) {
    failures.push(`${label}：${ratio.toFixed(2)}:1 < 4.5:1（${fgValue} on ${bgValue}）`)
  }
}

console.log(lines.join('\n'))
if (failures.length) {
  console.error('\n[check:contrast] 以下组合未达到 WCAG AA：')
  for (const failure of failures) {
    console.error(' - ' + failure)
  }
  process.exit(1)
}
console.log('\n[check:contrast] 全部通过（WCAG AA 4.5:1）')
