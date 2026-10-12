/**
 * 管理端接口探针（带真实 JWT）。
 *
 * JeecgBoot 的 token = JWT，签名密钥是**用户的 password 字段**（sys_user.password，16 位 hash），
 * claim: { username, clientType }，算法 HMAC256。
 * 这里在 Node 里用 crypto 直接造一个合法 token，绕过登录接口的验证码/加密流程，
 * 以便直接观察管理端接口的真实响应。
 */
import crypto from 'node:crypto';

const BASE = 'http://127.0.0.1:9951/api';
// 接口签名密钥（后端 jeecg.signatureSecret）：从环境变量读取，不写进仓库
//   $env:CK_SIGN_SECRET = '<值取自 ck-server/.env 的 JEECG_SIGNATURE_SECRET>'
const SIGN_SECRET = process.env.CK_SIGN_SECRET;
if (!SIGN_SECRET) {
  console.error('缺少 CK_SIGN_SECRET：设为后端 jeecg.signatureSecret 的值（见 ck-server/.env）');
  process.exit(2);
}

const USERNAME = 'admin';
/**
 * JWT 签名密钥是 sys_user.password 字段（不是明文密码）。
 * 不要把真实值写进仓库：用环境变量传入，例如
 *   $env:CK_PWD_FIELD = (从数据库读 sys_user.password)
 */
const PASSWORD_FIELD = process.env.CK_PWD_FIELD || '';
const CLIENT_TYPE = 'PC';

/**
 * 优先用环境变量传入的真实 token（从 Redis 的 prefix_user_token:* 取），
 * 取不到再退回自签 JWT。自签的签名虽正确，但服务端会校验 Redis 里的登录态，因此通常会被判失效。
 */
const REAL_TOKEN = process.env.CK_TOKEN || '';

function b64url(buf) {
  return Buffer.from(buf)
    .toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

function makeToken() {
  const header = { alg: 'HS256', typ: 'JWT' };
  const payload = {
    username: USERNAME,
    clientType: CLIENT_TYPE,
    exp: Math.floor(Date.now() / 1000) + 3600 * 8,
  };
  const signingInput = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
  const sig = crypto.createHmac('sha256', PASSWORD_FIELD).update(signingInput).digest();
  return `${signingInput}.${b64url(sig)}`;
}

function sign(query = {}) {
  const sorted = {};
  Object.keys(query)
    .filter((k) => k !== '_t')
    .sort()
    .forEach((k) => (sorted[k] = query[k]));
  return crypto.createHash('md5').update(JSON.stringify(sorted) + SIGN_SECRET).digest('hex').toUpperCase();
}

async function call(method, path, { query = {}, body, token } = {}) {
  const url = `${BASE}${path}?${new URLSearchParams(query).toString()}`;
  const headers = {
    'Content-Type': 'application/json',
    timestamp: String(Date.now()),
    sign: sign(query),
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

function show(label, r, { dumpResult = false } = {}) {
  const j = r.json || {};
  console.log(`\n--- ${label} ---`);
  console.log(`HTTP ${r.status}  success=${j.success}  code=${j.code}  message=${JSON.stringify(j.message)}`);
  const result = j.result;
  if (result === undefined || result === null) {
    console.log('result =', String(result));
  } else if (typeof result === 'string') {
    console.log(`result(字符串) = ${JSON.stringify(result)}`);
  } else if (Array.isArray(result)) {
    console.log(`result(数组) length=${result.length}`);
  } else {
    console.log('result 字段 =', Object.keys(result).slice(0, 15).join(', '));
    if (dumpResult) console.log('result 内容 =', JSON.stringify(result).slice(0, 500));
  }
  return j;
}

async function main() {
  const token = REAL_TOKEN || makeToken();
  console.log(REAL_TOKEN ? '使用环境变量传入的真实 token' : '使用自签 JWT（可能被判失效）');

  // A) 用合法 token 打管理端列表
  const list = await call('GET', '/shop/product/list', { query: { pageNo: 1, pageSize: 5 }, token });
  const lj = show('A. GET /shop/product/list（带 token）', list, { dumpResult: true });
  const first = lj?.result?.records?.[0];
  console.log('   首条 id =', first?.id, ' 标题 =', first?.title, ' categoryName =', first?.categoryName);

  const productId = first?.id;

  if (productId) {
    // B) 正常 id 查详情 —— 这是前端「保存并继续编辑」会走的请求
    const ok = await call('GET', '/shop/product/queryById', { query: { id: productId }, token });
    const oj = show(`B. GET /shop/product/queryById?id=${productId}（合法 id）`, ok);
    console.log('   specs =', oj?.result?.specs?.length, ' skus =', oj?.result?.skus?.length, ' images =', oj?.result?.images?.length);

    // C) 复现：把提示语当 id 去查（旧前端 bug 的请求）
    const bad = await call('GET', '/shop/product/queryById', { query: { id: '添加成功！' }, token });
    show('C. GET /shop/product/queryById?id=添加成功！ （旧前端 bug 的请求）', bad);

    // D) skuList（列表展开行）
    const sku = await call('GET', '/shop/product/skuList', { query: { id: productId }, token });
    const sj = show('D. GET /shop/product/skuList', sku);
    console.log('   sku 行数 =', Array.isArray(sj?.result) ? sj.result.length : 'n/a');
  }

  // E) 空 id / 缺 id 的表现
  const noId = await call('GET', '/shop/product/queryById', { query: {}, token });
  show('E. GET /shop/product/queryById（不带 id）', noId);

  // F) 品牌/标签列表（验证本地化名修复）
  const brands = await call('GET', '/shop/brand/list', { query: { pageNo: 1, pageSize: 5 }, token });
  show('F. GET /shop/brand/list', brands);
  const tags = await call('GET', '/shop/tag/list', { query: { pageNo: 1, pageSize: 5 }, token });
  show('G. GET /shop/tag/list', tags);
}

main().catch((e) => {
  console.error('探针失败:', e);
  process.exit(1);
});
