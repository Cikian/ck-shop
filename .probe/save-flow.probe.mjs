/**
 * 完整复现前端「新增商品 → 保存并继续编辑」的请求序列，逐步打印真实响应。
 * 目的：确认 POST /shop/product/add 返回的 result 到底是商品ID还是一句提示语。
 */
import crypto from 'node:crypto';

const BASE = 'http://127.0.0.1:9951/api';
// 接口签名密钥（后端 jeecg.signatureSecret）：从环境变量读取，不写进仓库
const SIGN_SECRET = process.env.CK_SIGN_SECRET;
if (!SIGN_SECRET) {
  console.error('缺少 CK_SIGN_SECRET：设为后端 jeecg.signatureSecret 的值（见 ck-server/.env）');
  process.exit(2);
}
const TOKEN = process.env.CK_TOKEN;

function sign(query = {}) {
  const sorted = {};
  Object.keys(query)
    .filter((k) => k !== '_t')
    .sort()
    .forEach((k) => (sorted[k] = query[k]));
  return crypto.createHash('md5').update(JSON.stringify(sorted) + SIGN_SECRET).digest('hex').toUpperCase();
}

async function call(method, path, { query = {}, body } = {}) {
  const url = `${BASE}${path}?${new URLSearchParams(query).toString()}`;
  const headers = {
    'Content-Type': 'application/json',
    timestamp: String(Date.now()),
    sign: sign(query),
    version: 'v3',
    'X-Access-Token': TOKEN,
  };
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

function describe(label, r) {
  const j = r.json || {};
  const t = typeof j.result;
  console.log(`\n--- ${label} ---`);
  console.log(`HTTP ${r.status} success=${j.success} code=${j.code}`);
  console.log(`message = ${JSON.stringify(j.message)}`);
  console.log(`result  (%${t}) = ${t === 'string' ? JSON.stringify(j.result) : t === 'object' ? JSON.stringify(j.result).slice(0, 200) : String(j.result)}`);
  return j;
}

async function main() {
  if (!TOKEN) {
    console.error('缺少 CK_TOKEN 环境变量');
    process.exit(1);
  }

  // 1) 全新商品（不传 id）→ 走 add
  const spu = 'SPU-PROBE-' + Date.now().toString().slice(-8);
  const payload = {
    spuCode: spu,
    price: 1234.56,
    unit: '件',
    sortOrder: 9,
    status: 'Y',
    isRecommend: 'N',
    tagIds: [],
    translations: [{ languageCode: 'zh', title: '探针商品 ' + spu, subTitle: '探针', description: '<p>probe</p>' }],
    images: [],
    specs: [
      {
        name: '颜色',
        sortOrder: 0,
        isRequired: 'Y',
        values: [
          { tempId: 'T_COLOR_RED', value: '红色', sortOrder: 0 },
          { tempId: 'T_COLOR_BLUE', value: '蓝色', sortOrder: 1 },
        ],
      },
    ],
    combinations: [
      { specValueRefs: ['T_COLOR_RED'], price: 1234.56, stock: 5, saleable: 'Y' },
      { specValueRefs: ['T_COLOR_BLUE'], price: 2234.56, stock: 7, saleable: 'Y' },
    ],
  };

  console.log('== 第 1 步：POST /shop/product/add（新增） ==');
  const addRes = await call('POST', '/shop/product/add', { body: payload });
  const aj = describe('POST /shop/product/add', addRes);

  const resultType = typeof aj.result;
  const newId = resultType === 'string' ? aj.result : aj.result?.id;
  console.log(`\n>>> 前端会取到的 newId = ${JSON.stringify(newId)}  (类型 ${typeof newId})`);
  if (resultType !== 'string') {
    console.log('>>> ⚠️ result 不是字符串：前端会把非字符串一律忽略，改用当前表单 id');
  }

  if (!newId || typeof newId !== 'string') {
    console.log('\n拿不到 id，停止后续验证');
    return;
  }

  // 2) 前端「保存并继续编辑」紧接着会做的请求
  console.log('\n== 第 2 步：前端随后的回填请求 ==');
  const detail = await call('GET', '/shop/product/queryById', { query: { id: newId } });
  const dj = describe(`GET /shop/product/queryById?id=${newId}`, detail);
  console.log(`specs = ${dj.result?.specs?.length}  skus = ${dj.result?.skus?.length}  stock = ${dj.result?.stock}  minPrice = ${dj.result?.minPrice}  maxPrice = ${dj.result?.maxPrice}`);

  // 3) 编辑（模拟第二次保存：带 id）
  console.log('\n== 第 3 步：POST /shop/product/edit（编辑，验证唯一键重复保存） ==');
  const editRes = await call('POST', '/shop/product/edit', { body: { ...payload, id: newId, price: 1999 } });
  const ej = describe('POST /shop/product/edit', editRes);
  console.log(`>>> 前端会取到的 newId = ${JSON.stringify(typeof ej.result === 'string' ? ej.result : null)}，兜底用 formData.id=${newId}`);

  const detail2 = await call('GET', '/shop/product/queryById', { query: { id: newId } });
  const d2 = describe('编辑后再次 queryById', detail2);
  console.log(`skus = ${d2.result?.skus?.length}  stock = ${d2.result?.stock}  minPrice = ${d2.result?.minPrice}  maxPrice = ${d2.result?.maxPrice}`);

  // 4) 清理：软删除探针商品
  console.log('\n== 第 4 步：清理（软删除探针商品） ==');
  const del = await call('DELETE', '/shop/product/delete', { query: { id: newId } });
  describe('DELETE /shop/product/delete', del);
}

main().catch((e) => {
  console.error('复现失败:', e);
  process.exit(1);
});
