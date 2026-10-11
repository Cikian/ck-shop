/**
 * 管理端商品保存链路探针。
 *
 * 用与 nuxt-shop/server/utils 相同的签名算法，直接打后端 9951，
 * 复现"保存 → 提示成功 → 两条未找到数据"的链路，观察每一步的真实响应。
 *
 * 用法（Node 22 可直接跑 TS 的类型剥离，这里用纯 JS 避免依赖）：
 *   node .probe/product-flow.probe.mjs
 */
import crypto from 'node:crypto';

const BASE = 'http://127.0.0.1:9951/api';
const SIGN_SECRET = 'dd05f1c54d63749eda95f9fa6d49v442a';

/** 与前端 signMd5 一致：排序后 JSON + secret 取 MD5 大写 */
function sign(query = {}) {
  const sorted = {};
  Object.keys(query)
    .filter((k) => k !== '_t')
    .sort()
    .forEach((k) => {
      sorted[k] = query[k];
    });
  return crypto.createHash('md5').update(JSON.stringify(sorted) + SIGN_SECRET).digest('hex').toUpperCase();
}

function buildQuery(path, query) {
  const params = { ...query };
  return { url: BASE + path + '?' + new URLSearchParams(params).toString(), params };
}

async function call(method, path, { query = {}, body, token } = {}) {
  const { url, params } = buildQuery(path, query);
  const headers = {
    'Content-Type': 'application/json',
    timestamp: String(Date.now()),
    sign: sign(params),
    version: 'v3',
  };
  if (token) headers['X-Access-Token'] = token;
  const res = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = { raw: text.slice(0, 300) };
  }
  return { status: res.status, json };
}

function show(label, r) {
  const j = r.json || {};
  console.log(`\n--- ${label} ---`);
  console.log(`HTTP ${r.status}  success=${j.success}  code=${j.code}  message=${JSON.stringify(j.message)}`);
  const result = j.result;
  if (result === undefined || result === null) {
    console.log('result =', result);
  } else if (typeof result === 'string') {
    console.log(`result(字符串) = ${JSON.stringify(result)}   ← 若这里不是商品ID，前端就会拿它去查详情`);
  } else if (Array.isArray(result)) {
    console.log(`result(数组) 长度 ${result.length}`);
  } else {
    console.log('result 顶层字段 =', Object.keys(result).slice(0, 12).join(', '));
  }
  return j;
}

async function main() {
  console.log('目标后端:', BASE);

  // 1) 店铺端列表（免登录，先确认服务活着、路由生效）
  const store = await call('GET', '/shop/product/store/list', { query: { lang: 'zh', pageNo: 1, pageSize: 2 } });
  const storeJson = show('1. GET /shop/product/store/list（免登录探活）', store);
  const demo = storeJson?.result?.records?.[0];
  console.log('   首条商品 id =', demo?.id, ' 标题 =', demo?.title);

  // 2) 管理端列表（需登录；未登录预期 401，用来确认鉴权状态）
  const list = await call('GET', '/shop/product/list', { query: { pageNo: 1, pageSize: 2 } });
  show('2. GET /shop/product/list（未带 token）', list);

  // 3) 用已知的演示商品 id 查详情（免登录的 store 详情 + 需登录的管理端详情）
  if (demo?.id) {
    const detail = await call('GET', '/shop/product/store/detail', { query: { id: demo.id, lang: 'zh' } });
    const dj = show('3. GET /shop/product/store/detail?id=' + demo.id, detail);
    console.log('   specs 组数 =', dj?.result?.specs?.length, ' combinations 条数 =', dj?.result?.combinations?.length);
  }

  // 4) 故意用一个不存在的 id，看后端到底回什么（复现前端"未找到数据"的来源）
  const bad = await call('GET', '/shop/product/store/detail', { query: { id: '添加成功！', lang: 'zh' } });
  show('4. GET /shop/product/store/detail?id=添加成功！ （复现错误 id 的场景）', bad);

  // 5) 管理端 queryById 用错误 id（未登录时先看鉴权，再判断是否能拿到"未找到数据"）
  const q = await call('GET', '/shop/product/queryById', { query: { id: '添加成功！' } });
  show('5. GET /shop/product/queryById?id=添加成功！ （未带 token）', q);
}

main().catch((e) => {
  console.error('探针失败:', e.message);
  process.exit(1);
});
