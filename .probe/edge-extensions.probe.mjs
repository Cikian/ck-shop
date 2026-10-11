/** 解析 Edge 扩展的真实名称（manifest 用 __MSG_xxx__ 国际化，需要读 _locales） */
import fs from 'node:fs';
import path from 'node:path';

const ud = path.join(process.env.LOCALAPPDATA, 'Microsoft', 'Edge', 'User Data');
const extDir = path.join(ud, 'Default', 'Extensions');
const read = (p) => {
  try {
    return JSON.parse(fs.readFileSync(p, 'utf8'));
  } catch {
    return null;
  }
};

for (const id of fs.readdirSync(extDir)) {
  const dir = path.join(extDir, id);
  const vers = fs.readdirSync(dir).filter((v) => fs.statSync(path.join(dir, v)).isDirectory());
  const ver = vers[vers.length - 1];
  const root = path.join(dir, ver);
  const mf = read(path.join(root, 'manifest.json'));
  if (!mf) continue;
  const loc = mf.default_locale || 'en';
  const msg = read(path.join(root, '_locales', loc, 'messages.json')) || read(path.join(root, '_locales', 'en', 'messages.json')) || {};
  const resolve = (s) => (typeof s === 'string' ? s.replace(/__MSG_([A-Za-z0-9_@]+)__/g, (_, k) => msg[k]?.message || msg[k.toLowerCase()]?.message || _) : s);
  const name = resolve(mf.name);
  const desc = resolve(mf.description || '');
  const hasScripts = (mf.content_scripts || []).length;
  const hasDevtools = !!mf.devtools_page;
  const matches = (mf.content_scripts || []).flatMap((cs) => cs.matches || []).slice(0, 4);
  console.log(`${name}  [${id}]  v${mf.version}`);
  if (desc) console.log(`    描述: ${String(desc).slice(0, 120)}`);
  console.log(`    content_scripts=${hasScripts} devtools_page=${hasDevtools} manifest=${mf.manifest_version} matches=${matches.join(' | ') || '-'}`);
  console.log('');
}
