/** 读取本机 Edge profile 的 DevTools 设置（只读，全量列出标量键） */
import fs from 'node:fs';
import path from 'node:path';

const prefPath = path.join(process.env.LOCALAPPDATA, 'Microsoft', 'Edge', 'User Data', 'Default', 'Preferences');
const raw = JSON.parse(fs.readFileSync(prefPath, 'utf8'));
const dt = raw.devtools || {};

function scalarize(obj, prefix = '', out = {}) {
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v === null || ['string', 'number', 'boolean'].includes(typeof v)) {
      out[key] = v;
    } else if (typeof v === 'object' && !Array.isArray(v)) {
      const keys = Object.keys(v);
      if (keys.length <= 12) scalarize(v, key, out);
      else out[key] = `<object ${keys.length} keys>`;
    } else if (Array.isArray(v)) {
      out[key] = `<array ${v.length}>`;
    }
  }
  return out;
}

const prefs = dt.preferences || {};
const scalars = scalarize(prefs);
console.log(`devtools.preferences 标量项共 ${Object.keys(scalars).length} 个\n`);

const RE = /preserve|log|dock|cache|throttl|network|pause|breakpoint|cacheDisabled|panel|disabled|userAgent|emulat|offline|block|condition/i;
for (const [k, v] of Object.entries(scalars)) {
  if (RE.test(k)) console.log(`  ${k} = ${JSON.stringify(v)}`);
}
console.log('\n--- 全部键名 ---');
console.log(Object.keys(scalars).join('\n'));
