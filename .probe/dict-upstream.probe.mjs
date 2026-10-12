/**
 * 校验「上游来源」字典是否可被管理端读取。
 *
 * 目的：确认新增字典 shop_upstream_source 后，前端下拉框能真正取到选项
 * （即 /sys/dict/getDictItems/{code} 返回淘宝/京东/1688/闲鱼），
 * 而不是只看数据库里有没有行。
 *
 * token 从 Redis 的 prefix_user_token:* 取，用环境变量传入，不写进仓库：
 *   $env:CK_TOKEN = '<JWT>'
 */
import crypto from 'node:crypto';

const BASE = 'http://127.0.0.1:9951/api';
// 接口签名密钥（后端 jeecg.signatureSecret）：从环境变量读取，不写进仓库
const SIGN_SECRET = process.env.CK_SIGN_SECRET;
if (!SIGN_SECRET) {
  console.error('缺少 CK_SIGN_SECRET：设为后端 jeecg.signatureSecret 的值（见 ck-server/.env）');
  process.exit(2);
}
const TOKEN = process.env.CK_TOKEN || '';

function sign(query = {}) {
  const sorted = {};
  Object.keys(query)
    .filter((k) => k !== '_t')
    .sort()
    .forEach((k) => (sorted[k] = query[k]));
  return crypto.createHash('md5').update(JSON.stringify(sorted) + SIGN_SECRET).digest('hex').toUpperCase();
}

async function call(path, { query = {} } = {}) {
  const url = `${BASE}${path}?${new URLSearchParams(query).toString()}`;
  const res = await fetch(url, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      // 注意：header 名必须是 X-TIMESTAMP / X-Sign（见 CommonConstant），
      // 写成 timestamp / sign 会因为名字对不上而被判"时间戳为空"。
      'X-TIMESTAMP': String(Date.now()),
      'X-Sign': sign(query),
      version: 'v3',
      'X-Access-Token': TOKEN,
    },
  });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = { raw: text.slice(0, 300) };
  }
  return { status: res.status, json };
}

async function main() {
  if (!TOKEN) {
    console.error('缺少 CK_TOKEN 环境变量');
    process.exit(1);
  }

  // 1) 字典接口：这是前端下拉框的数据来源
  const dict = await call('/sys/dict/getDictItems/shop_upstream_source');
  console.log('--- GET /sys/dict/getDictItems/shop_upstream_source ---');
  console.log(`HTTP ${dict.status}  success=${dict.json.success}  message=${JSON.stringify(dict.json.message)}`);
  const items = dict.json.result;
  if (Array.isArray(items)) {
    console.log(`选项数量 = ${items.length}`);
    for (const it of items) {
      console.log(`  value=${JSON.stringify(it.value)}  text=${JSON.stringify(it.text ?? it.label)}  color=${JSON.stringify(it.color)}`);
    }
  } else {
    console.log('result =', JSON.stringify(items));
  }

  // 2) 当前运行中的后端是否已带上新字段（未重启时应当没有）
  const list = await call('/shop/product/list', { query: { pageNo: 1, pageSize: 1 } });
  const first = list.json?.result?.records?.[0];
  console.log('\n--- 运行中后端的新字段可见性（未重启时应为 false）---');
  console.log('商品 id =', first?.id);
  console.log('含 upstreamSource 键 =', first ? Object.prototype.hasOwnProperty.call(first, 'upstreamSource') : 'n/a');
  console.log('含 upstreamUrl 键  =', first ? Object.prototype.hasOwnProperty.call(first, 'upstreamUrl') : 'n/a');
}

main().catch((e) => {
  console.error('探针失败:', e);
  process.exit(1);
});
