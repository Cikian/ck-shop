// scripts/audit-vhtml.mjs
//
// v-html 使用审计（清单 B18）。
//
// 为什么需要：管理端 token 存在 localStorage，任何一处 XSS 都能直接把 token 读走。
// 而 `v-html` 是 Vue 里唯一的"原样插入 HTML"入口 —— 只要插进去的内容里有
// <script> 之外的任意事件属性（onerror/onclick…），就是一次可执行的注入。
//
// 这个脚本不判断"安全不安全"（那需要看数据来源），只负责：
//   1. 列出当前所有 v-html 用法（文件:行 + 表达式），让每次改动都能被 review；
//   2. 数量超过基线时退出码 1，防止"悄悄多插一处 v-html"。
//
// 用法：
//   node scripts/audit-vhtml.mjs            # 报告
//   VHTML_MAX=40 node scripts/audit-vhtml.mjs  # 超过 40 处时失败

import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const SRC_DIR = 'src'
const DEFAULT_MAX = 36

function walk(dir) {
  const out = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue
    const full = join(dir, entry.name)
    if (entry.isDirectory()) {
      out.push(...walk(full))
    } else if (entry.name.endsWith('.vue')) {
      out.push(full)
    }
  }
  return out
}

const hits = []
for (const file of walk(SRC_DIR)) {
  const lines = readFileSync(file, 'utf8').split(/\r?\n/)
  lines.forEach((line, index) => {
    if (line.includes('v-html')) {
      hits.push({
        file: file.split(/[\\/]/).join('/'),
        line: index + 1,
        text: line.trim().slice(0, 140),
      })
    }
  })
}

console.log(`v-html 用法：${hits.length} 处`)
for (const hit of hits) {
  console.log(`  ${hit.file}:${hit.line}  ${hit.text}`)
}

const max = Number(process.env.VHTML_MAX || DEFAULT_MAX)
if (hits.length > max) {
  console.error(`\n✗ v-html 数量 ${hits.length} 超过基线 ${max}：新增的用法必须确认数据来源可信（或改用纯文本/白名单净化）`)
  process.exit(1)
}
console.log(`\n✓ 未超过基线（${max}）`)
