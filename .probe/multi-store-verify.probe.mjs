/**
 * 多店铺改造 · 端到端验收探针
 *
 * 覆盖 docs/多店铺改造-执行方案.md 的「功能验收」清单：
 *   1) 商品列表按部门隔离（运营只看本部门 / 管理员不限）
 *   2) 新增商品 sys_org_code 自动等于操作人当前部门
 *   3) 运营改商品归属 → 后端拒绝（不是前端隐藏）
 *   4) 按店铺筛选 = 按部门归属筛选，未知店铺报错而不是返回全量
 *   5) 前台按店铺（部门）过滤；跨店详情/SPU 路由被拒
 *   6) 分类树按店过滤
 *   7) 品牌接口已消失
 *
 * 用法：
 *   # 可选：先生成一个"户外部运营"的 token（打印到 stdout，再由调用方写入 Redis）
 *   node .probe/multi-store-verify.probe.mjs --mint-op
 *
 *   $env:CK_TOKEN='<admin JWT>'
 *   $env:CK_OP_TOKEN='<shopop JWT>'
 *   node .probe/multi-store-verify.probe.mjs
 *
 * 说明：
 *   - 所有测试数据都用 SPU-PROBE-MS-* 前缀，脚本结束时会自己删掉（逻辑删除）。
 *   - admin token 从 Redis 取：redis-cli --scan --pattern 'prefix_user_token:*'
 */
import crypto from 'node:crypto';

const BASE = process.env.CK_BASE || 'http://127.0.0.1:9951/api';
const ADMIN_TOKEN = process.env.CK_TOKEN || '';
const OP_TOKEN = process.env.CK_OP_TOKEN || '';
// 接口签名密钥（后端 jeecg.signatureSecret）：从环境变量读取，不写进仓库
//   $env:CK_SIGN_SECRET = '<值取自 ck-server/.env 的 JEECG_SIGNATURE_SECRET>'
const SIGN_SECRET = process.env.CK_SIGN_SECRET;
if (!SIGN_SECRET) {
  console.error('缺少 CK_SIGN_SECRET：设为后端 jeecg.signatureSecret 的值（见 ck-server/.env）');
  process.exit(2);
}
const CLIENT_TYPE = 'PC';
const SPU_PREFIX = 'SPU-PROBE-MS-';

/** 户外部 / 数码部 的部门编码，与数据库实际值一致 */
const ORG_DIGITAL = 'A06A01';
const ORG_OUTDOOR = 'A06A02';

// ---------------------------------------------------------------------------
// 小工具
// ---------------------------------------------------------------------------

function b64url(buf) {
  return Buffer.from(buf).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

/** 与后端 JwtUtil.sign 等价：HMAC256，密钥 = sys_user.password 字段值 */
export function mintToken(username, passwordField, expSeconds = 3600) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const payload = { username, clientType: CLIENT_TYPE, exp: Math.floor(Date.now() / 1000) + expSeconds };
  const input = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
  const sig = crypto.createHmac('sha256', passwordField).update(input).digest();
  return `${input}.${b64url(sig)}`;
}

function sign(query = {}) {
  const sorted = {};
  Object.keys(query)
    .filter((k) => k !== '_t')
    .sort()
    .forEach((k) => (sorted[k] = String(query[k])));
  return crypto.createHash('md5').update(JSON.stringify(sorted) + SIGN_SECRET).digest('hex').toUpperCase();
}

async function call(method, path, { query = {}, body, token, storeCode } = {}) {
  const qs = new URLSearchParams(query).toString();
  const url = `${BASE}${path}${qs ? `?${qs}` : ''}`;
  const headers = {
    'Content-Type': 'application/json',
    'X-TIMESTAMP': String(Date.now()),
    'X-Sign': sign(query),
    version: 'v3',
  };
  if (token) headers['X-Access-Token'] = token;
  if (storeCode) headers['X-Store-Code'] = storeCode;
  const res = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = { raw: text.slice(0, 200) };
  }
  return { status: res.status, json, ok: res.ok && json?.success !== false };
}

const results = [];
function check(name, passed, detail = '') {
  results.push({ name, passed: !!passed, detail });
  console.log(`${passed ? '  PASS' : '  FAIL'}  ${name}${detail ? `  ← ${detail}` : ''}`);
}

function ids(page) {
  return (page?.records || []).map((r) => r.id);
}

function productBody(spuCode, title, extra = {}) {
  return {
    spuCode,
    price: 9.9,
    unit: '件',
    status: 'Y',
    isRecommend: 'N',
    sortOrder: 0,
    mainImage: null,
    tagIds: [],
    images: [],
    specs: [],
    combinations: [],
    translations: [{ languageCode: 'zh', title, subTitle: '', description: '', coverUrl: '' }],
    ...extra,
  };
}

// ---------------------------------------------------------------------------
// 主流程
// ---------------------------------------------------------------------------

async function main() {
  if (!ADMIN_TOKEN) {
    console.error('缺少 CK_TOKEN（管理员 JWT）');
    process.exit(2);
  }
  console.log(`BASE = ${BASE}`);
  console.log(`运营 token = ${OP_TOKEN ? '已提供' : '未提供（跳过运营相关用例）'}`);

  const created = []; // 本次探针创建的商品 id，最后统一清理

  try {
    // ===================== 一、基础只读契约 =====================
    console.log('\n[一] 管理端只读契约');

    const storeRes = await call('GET', '/shop/store/all', { token: ADMIN_TOKEN });
    const stores = Array.isArray(storeRes.json?.result) ? storeRes.json.result : [];
    const byCode = Object.fromEntries(stores.map((s) => [s.code, s]));
    check('店铺列表可用', stores.length > 0, `共 ${stores.length} 个店铺`);
    check(
      '店铺带出绑定部门 id 与只读派生 departOrgCode',
      stores.every((s) => 'departId' in s) && byCode.default?.departOrgCode === ORG_DIGITAL,
      `default.departOrgCode=${byCode.default?.departOrgCode}`
    );
    check(
      '户外部店铺绑定到 A06A02',
      byCode.outdoor?.departOrgCode === ORG_OUTDOOR,
      `outdoor.departOrgCode=${byCode.outdoor?.departOrgCode}`
    );
    const defaultStoreId = byCode.default?.id;
    const outdoorStoreId = byCode.outdoor?.id;

    const listRes = await call('GET', '/shop/product/list', { query: { pageNo: 1, pageSize: 50 }, token: ADMIN_TOKEN });
    const listRecords = listRes.json?.result?.records || [];
    check('管理员商品列表可用', listRes.ok, `total=${listRes.json?.result?.total}`);
    check(
      '列表项带 sysOrgCode（= 归属部门）',
      listRecords.length > 0 && listRecords.every((r) => typeof r.sysOrgCode === 'string'),
      `首条 sysOrgCode=${listRecords[0]?.sysOrgCode}`
    );
    check(
      '列表项不再返回 品牌/上架渠道 字段',
      listRecords.every((r) => !('brandId' in r) && !('brandName' in r) && !('stores' in r))
    );

    const catRes = await call('GET', '/shop/category/list', { query: { pageNo: 1, pageSize: 50 }, token: ADMIN_TOKEN });
    const catRecords = catRes.json?.result?.records || [];
    check('分类实体已换成 sysOrgCode（无 storeId）', catRes.ok && catRecords.every((r) => 'sysOrgCode' in r && !('storeId' in r)),
      `共 ${catRecords.length} 条`);

    const brandRes = await call('GET', '/shop/brand/list', { query: { pageNo: 1, pageSize: 5 }, token: ADMIN_TOKEN });
    check('品牌接口已移除', !brandRes.ok, `HTTP ${brandRes.status} success=${brandRes.json?.success}`);

    // ===================== 二、按店铺（部门）筛选 =====================
    console.log('\n[二] 按店铺筛选 = 按部门归属筛选（先造两条数据）');

    const spuD = `${SPU_PREFIX}D${Date.now().toString(36)}`;
    const spuO = `${SPU_PREFIX}O${Date.now().toString(36)}`;
    const addD = await call('POST', '/shop/product/add', {
      token: ADMIN_TOKEN,
      body: productBody(spuD, '探针-数码部商品', { sysOrgCode: ORG_DIGITAL }),
    });
    check('管理员可跨部门新增（归属数码部 A06A01）', addD.ok, addD.json?.message);
    const idD = addD.json?.result;
    if (idD) created.push(idD);

    const addO = await call('POST', '/shop/product/add', {
      token: ADMIN_TOKEN,
      body: productBody(spuO, '探针-户外部商品', { sysOrgCode: ORG_OUTDOOR }),
    });
    check('管理员可跨部门新增（归属户外部 A06A02）', addO.ok, addO.json?.message);
    const idO = addO.json?.result;
    if (idO) created.push(idO);

    const detailD = await call('GET', '/shop/product/queryById', { query: { id: idD }, token: ADMIN_TOKEN });
    check('新增后归属落库为 A06A01', detailD.json?.result?.sysOrgCode === ORG_DIGITAL, `sysOrgCode=${detailD.json?.result?.sysOrgCode}`);
    const detailO = await call('GET', '/shop/product/queryById', { query: { id: idO }, token: ADMIN_TOKEN });
    check('跨部门指定归属生效 A06A02', detailO.json?.result?.sysOrgCode === ORG_OUTDOOR, `sysOrgCode=${detailO.json?.result?.sysOrgCode}`);
    check('详情不再返回品牌/渠道字段', detailD.ok && !('brandName' in (detailD.json?.result || {})) && !('stores' in (detailD.json?.result || {})));

    const byDefault = await call('GET', '/shop/product/list', { query: { pageNo: 1, pageSize: 50, storeId: defaultStoreId }, token: ADMIN_TOKEN });
    const byOutdoor = await call('GET', '/shop/product/list', { query: { pageNo: 1, pageSize: 50, storeId: outdoorStoreId }, token: ADMIN_TOKEN });
    const defaultIds = ids(byDefault.json?.result);
    const outdoorIds = ids(byOutdoor.json?.result);
    check('按数码店铺筛选 → 含数码商品、不含户外商品', defaultIds.includes(idD) && !defaultIds.includes(idO), `total=${byDefault.json?.result?.total}`);
    check('按户外店铺筛选 → 含户外商品、不含数码商品', outdoorIds.includes(idO) && !outdoorIds.includes(idD), `total=${byOutdoor.json?.result?.total}`);
    const bogus = await call('GET', '/shop/product/list', { query: { pageNo: 1, pageSize: 10, storeId: 'not-a-real-store-id' }, token: ADMIN_TOKEN });
    check('未知店铺筛选直接报错（不静默返回全量）', !bogus.ok, bogus.json?.message);

    const catOutdoor = await call('GET', '/shop/category/list', { query: { pageNo: 1, pageSize: 50, sysOrgCode: ORG_OUTDOOR }, token: ADMIN_TOKEN });
    check('分类按归属部门过滤可用（户外部应为 0 条）', (catOutdoor.json?.result?.total ?? -1) === 0, `total=${catOutdoor.json?.result?.total}`);

    // ===================== 三、店铺端（前台） =====================
    console.log('\n[三] 店铺端（X-Store-Code 解析 → 部门归属）');

    const frontDefault = await call('GET', '/shop/product/store/list', { query: { pageNo: 1, pageSize: 50 }, storeCode: 'default' });
    const frontOutdoor = await call('GET', '/shop/product/store/list', { query: { pageNo: 1, pageSize: 50 }, storeCode: 'outdoor' });
    const fDefaultIds = ids(frontDefault.json?.result);
    const fOutdoorIds = ids(frontOutdoor.json?.result);
    check('default 店铺前台可见数码部商品', fDefaultIds.includes(idD) && !fDefaultIds.includes(idO), `total=${frontDefault.json?.result?.total}`);
    check('outdoor 店铺前台只见户外部商品', fOutdoorIds.includes(idO) && !fOutdoorIds.includes(idD), `total=${frontOutdoor.json?.result?.total}`);
    check('前台卡片不再返回品牌字段', (frontDefault.json?.result?.records || []).every((r) => !('brandId' in r) && !('brandName' in r)));

    const frontBad = await call('GET', '/shop/product/store/list', { query: { pageNo: 1, pageSize: 5 }, storeCode: 'no-such-store' });
    check('未识别的店铺编码报错（绝不回退全量）', !frontBad.ok, frontBad.json?.message);

    const crossId = await call('GET', '/shop/product/store/detail', { query: { id: idD }, storeCode: 'outdoor' });
    check('户外店按 id 打开数码部商品详情 → 被拒', !crossId.ok, crossId.json?.message);
    const sameId = await call('GET', '/shop/product/store/detail', { query: { id: idD }, storeCode: 'default' });
    check('数码店按 id 打开数码部商品详情 → 通过', sameId.ok && sameId.json?.result?.id === idD);
    const crossSpu = await call('GET', '/shop/product/store/detail', { query: { spuCode: spuD }, storeCode: 'outdoor' });
    check('户外店按 spuCode 打开数码部商品 → 被拒（spu_code 按部门唯一）', !crossSpu.ok, crossSpu.json?.message);

    const treeDefault = await call('GET', '/shop/store/category/tree', { query: { lang: 'zh' }, storeCode: 'default' });
    const treeOutdoor = await call('GET', '/shop/store/category/tree', { query: { lang: 'zh' }, storeCode: 'outdoor' });
    check('数码店分类树非空', Array.isArray(treeDefault.json?.result) && treeDefault.json.result.length > 0, `roots=${treeDefault.json?.result?.length}`);
    check('户外店分类树为空（分类归属已按部门隔离）', Array.isArray(treeOutdoor.json?.result) && treeOutdoor.json.result.length === 0, `roots=${treeOutdoor.json?.result?.length}`);

    // ===================== 四、运营（受限账号） =====================
    if (OP_TOKEN) {
      console.log('\n[四] 运营账号（部门 A06A02，无 shop-store:manage-all）');

      const opList = await call('GET', '/shop/product/list', { query: { pageNo: 1, pageSize: 50 }, token: OP_TOKEN });
      const opRecords = opList.json?.result?.records || [];
      check('运营商品列表仅含本部门数据', opList.ok && opRecords.length > 0 && opRecords.every((r) => String(r.sysOrgCode).startsWith(ORG_OUTDOOR)),
        `total=${opList.json?.result?.total} sysOrgCode=${opRecords.map((r) => r.sysOrgCode).join(',')}`);
      check('运营看不到数码部商品', !ids(opList.json?.result).includes(idD));

      const opByDefault = await call('GET', '/shop/product/list', { query: { pageNo: 1, pageSize: 50, storeId: defaultStoreId }, token: OP_TOKEN });
      check('运营按非同部门店铺筛选 → 空结果（可见域取交集，不是全量）', (opByDefault.json?.result?.total ?? -1) === 0,
        `total=${opByDefault.json?.result?.total}`);

      const opAdd = await call('POST', '/shop/product/add', {
        token: OP_TOKEN,
        body: productBody(`${SPU_PREFIX}OWN${Date.now().toString(36)}`, '探针-运营自建商品'),
      });
      const opProductId = opAdd.json?.result;
      if (opProductId) created.push(opProductId);
      check('运营新增商品成功（归属自动取操作人部门）', opAdd.ok, opAdd.json?.message);
      const opCreated = await call('GET', '/shop/product/queryById', { query: { id: opProductId }, token: OP_TOKEN });
      check('运营新增商品 sys_org_code 自动 = A06A02', opCreated.json?.result?.sysOrgCode === ORG_OUTDOOR, `sysOrgCode=${opCreated.json?.result?.sysOrgCode}`);

      const opEditOwn = await call('POST', '/shop/product/edit', {
        token: OP_TOKEN,
        body: productBody(String(opCreated.json?.result?.spuCode), '探针-运营自建商品(改标题)', { id: opProductId, sysOrgCode: ORG_OUTDOOR }),
      });
      check('运营可编辑本部门商品（归属不变）', opEditOwn.ok, opEditOwn.json?.message);

      const opSteal = await call('POST', '/shop/product/edit', {
        token: OP_TOKEN,
        body: productBody(String(opCreated.json?.result?.spuCode), '探针-越权改归属', { id: opProductId, sysOrgCode: ORG_DIGITAL }),
      });
      check('运营改归属 → 后端拒绝（硬校验）', !opSteal.ok, opSteal.json?.message);

      const opReadOther = await call('GET', '/shop/product/queryById', { query: { id: idD }, token: OP_TOKEN });
      check('运营读取他部门商品详情 → 拒绝', !opReadOther.ok, opReadOther.json?.message);

      const opDeleteOther = await call('DELETE', '/shop/product/delete', { query: { id: idD }, token: OP_TOKEN });
      check('运营删除他部门商品 → 拒绝', !opDeleteOther.ok, opDeleteOther.json?.message);

      const opEditOther = await call('POST', '/shop/product/edit', {
        token: OP_TOKEN,
        body: productBody(spuD, '探针-越权改他部门商品', { id: idD }),
      });
      check('运营编辑他部门商品 → 拒绝', !opEditOther.ok, opEditOther.json?.message);
    } else {
      console.log('\n[四] 运营账号用例：跳过（未提供 CK_OP_TOKEN）');
    }
  } finally {
    // ===================== 清理 =====================
    console.log('\n[五] 清理探针数据（逻辑删除）');
    for (const id of created) {
      const del = await call('DELETE', '/shop/product/delete', { query: { id }, token: ADMIN_TOKEN });
      console.log(`  删除 ${id} → ${del.ok ? '成功' : `失败：${del.json?.message}`}`);
    }
  }

  const failed = results.filter((r) => !r.passed);
  console.log(`\n===== 结果：${results.length - failed.length}/${results.length} 通过 =====`);
  if (failed.length) {
    console.log('失败用例：');
    failed.forEach((r) => console.log(`  - ${r.name}${r.detail ? `  ← ${r.detail}` : ''}`));
    process.exit(1);
  }
}

const isMint = process.argv.includes('--mint-op');
const isRegister = process.argv.includes('--register-op');

/**
 * 极简 RESP 客户端（与 .probe/redis-token.probe.mjs 同一套做法）。
 * 注意：JeecgBoot 的 RedisTemplate 对 value 用的是 JSON 序列化，
 * 所以必须写入 **带引号的 JSON 字符串**（"eyJ..."），写裸字符串会让
 * ShiroRealm.checkUserTokenIsEffect 反序列化失败 → 认证异常。
 */
async function redisSet(key, value, ttlSeconds) {
  const net = await import('node:net');
  const enc = (args) => {
    const parts = [Buffer.from(`*${args.length}\r\n`)];
    for (const a of args) {
      const s = Buffer.from(String(a));
      parts.push(Buffer.from(`$${s.length}\r\n`), s, Buffer.from('\r\n'));
    }
    return Buffer.concat(parts);
  };
  const sock = net.createConnection({ host: process.env.REDIS_HOST || '127.0.0.1', port: Number(process.env.REDIS_PORT || 6379) });
  await new Promise((res, rej) => {
    sock.once('connect', res);
    sock.once('error', rej);
  });
  sock.write(enc(['SET', key, value, 'EX', String(ttlSeconds)]));
  const reply = await new Promise((res) => sock.once('data', (d) => res(d.toString('utf8').trim())));
  sock.end();
  return reply;
}

if (isMint || isRegister) {
  const pwd = process.env.CK_OP_PWD || '';
  if (!pwd) {
    console.error('缺少 CK_OP_PWD（shopop 的 sys_user.password 字段值）');
    process.exit(2);
  }
  const username = process.env.CK_OP_USER || 'shopop';
  // 注册模式优先用调用方已持有的 token：--mint-op 每次都会生成新 exp，两次调用得到的 token 不同，
  // 若各自 mint 一次，注册的和拿去请求的就不是同一个，认证必然失败。
  const token = process.env.CK_OP_TOKEN || mintToken(username, pwd, 86400);
  if (isRegister) {
    const key = `prefix_user_token:${token}`;
    const reply = await redisSet(key, JSON.stringify(token), 86400);
    console.log(`已写入 ${key} → ${reply}`);
  } else {
    console.log(token);
  }
} else {
  main().catch((e) => {
    console.error('探针异常：', e);
    process.exit(1);
  });
}
