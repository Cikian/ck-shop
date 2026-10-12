/**
 * 首页轮播图 · 分店铺管理 验收探针
 *
 * 覆盖：
 *   1) 店铺端只读本店轮播图（解析不到店铺报错，不回退全量）
 *   2) 管理端按店铺（部门）隔离：运营只看/只写本部门，总部可不限
 *   3) 运营跨店读/写被拒
 *   4) 写接口不再匿名可调（Shiro 路径级 anon 已收敛为方法级 @IgnoreAuth）
 *
 * 用法：
 *   $env:CK_TOKEN='<admin JWT>'
 *   $env:CK_OP_TOKEN='<shopop JWT>'
 *   $env:CK_BASE='http://127.0.0.1:9951/api'   # 默认就是它
 *   node .probe/slideshow-scope.probe.mjs
 *
 * 测试数据带 `PROBE-SLIDE-` 前缀的描述，结束时会删除自己创建的那一条。
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

const ORG_DIGITAL = 'A06A01';
const ORG_OUTDOOR = 'A06A02';
const MARK = `PROBE-SLIDE-${Date.now().toString(36)}`;

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
  const headers = { 'Content-Type': 'application/json', 'X-TIMESTAMP': String(Date.now()), 'X-Sign': sign(query), version: 'v3' };
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

/** 轮播图列表在 Result 里是数组（按语言分组后的扁平行集） */
function slides(res) {
  const r = res.json?.result;
  return Array.isArray(r) ? r : [];
}

function slideBody(title, orgCode) {
  return {
    desc: MARK,
    goods: '',
    sort: 99,
    enabled: 'Y',
    sysOrgCode: orgCode,
    en: { pic_url_en: 'https://download.cikian.cn/probe-slide.png', title_en: title },
    zh: { pic_url_zh: 'https://download.cikian.cn/probe-slide.png', title_zh: title },
    fr: { pic_url_fr: 'https://download.cikian.cn/probe-slide.png', title_fr: title },
    jp: { pic_url_jp: 'https://download.cikian.cn/probe-slide.png', title_jp: title },
  };
}

async function main() {
  if (!ADMIN_TOKEN) {
    console.error('缺少 CK_TOKEN（管理员 JWT）');
    process.exit(2);
  }
  console.log(`BASE = ${BASE}`);
  console.log(`运营 token = ${OP_TOKEN ? '已提供' : '未提供（跳过运营用例）'}\n`);

  let createdId = null;
  try {
    // ================= A. 店铺端（免登录） =================
    console.log('[A] 店铺端按店铺读取');
    const frontDefault = await call('GET', '/home/slide/store', { storeCode: 'default' });
    const frontOutdoor = await call('GET', '/home/slide/store', { storeCode: 'outdoor' });
    check('default 店铺前台有轮播图', frontDefault.ok && slides(frontDefault).length > 0, `count=${slides(frontDefault).length}`);
    check('outdoor 店铺前台没有轮播图（户外部未配置）', frontOutdoor.ok && slides(frontOutdoor).length === 0, `count=${slides(frontOutdoor).length}`);
    const frontBad = await call('GET', '/home/slide/store', { storeCode: 'no-such-store' });
    check('未识别店铺编码报错（不回退全量）', !frontBad.ok, frontBad.json?.message);

    const anonWrite = await call('POST', '/home/slide', { body: slideBody('匿名写入尝试', ORG_DIGITAL) });
    check('写接口已不能匿名调用', !anonWrite.ok, `HTTP ${anonWrite.status} message=${anonWrite.json?.message}`);

    // ================= B. 管理员（不限部门） =================
    console.log('\n[B] 管理员（shop-store:manage-all / admin）');
    const adminAll = await call('GET', '/home/slide/all', { token: ADMIN_TOKEN });
    const adminDigital = await call('GET', '/home/slide/all', { query: { sysOrgCode: ORG_DIGITAL }, token: ADMIN_TOKEN });
    const adminOutdoor = await call('GET', '/home/slide/all', { query: { sysOrgCode: ORG_OUTDOOR }, token: ADMIN_TOKEN });
    check('不传店铺 = 总览（含数码部轮播图）', adminAll.ok && slides(adminAll).length >= 2, `count=${slides(adminAll).length}`);
    check('指定数码部 = 该店轮播图', adminDigital.ok && slides(adminDigital).length === 2, `count=${slides(adminDigital).length}`);
    check('指定户外部 = 空', adminOutdoor.ok && slides(adminOutdoor).length === 0, `count=${slides(adminOutdoor).length}`);
    check('返回项带归属 sysOrgCode', slides(adminDigital).every((s) => s.sysOrgCode === ORG_DIGITAL), `首条=${slides(adminDigital)[0]?.sysOrgCode}`);

    // ================= C. 运营（户外部，无 manage-all） =================
    if (OP_TOKEN) {
      console.log('\n[C] 运营账号（部门 A06A02）');
      const opAll = await call('GET', '/home/slide/all', { token: OP_TOKEN });
      check('不传店铺时被限定到本部门（而不是全量）', opAll.ok && slides(opAll).length === 0, `count=${slides(opAll).length}`);
      const opStealRead = await call('GET', '/home/slide/all', { query: { sysOrgCode: ORG_DIGITAL }, token: OP_TOKEN });
      check('运营读别的店铺 → 拒绝', !opStealRead.ok, opStealRead.json?.message);

      const opCreate = await call('POST', '/home/slide', { body: slideBody('户外部运营建的轮播图', ORG_OUTDOOR), token: OP_TOKEN });
      check('运营在自己店铺新增 → 成功', opCreate.ok, opCreate.json?.message);
      const afterCreate = await call('GET', '/home/slide/all', { query: { sysOrgCode: ORG_OUTDOOR }, token: OP_TOKEN });
      const mine = slides(afterCreate).find((s) => s.desc === MARK);
      createdId = mine?.id || null;
      check('新增后能在本店列表里读到（归属=A06A02）', !!createdId && mine?.sysOrgCode === ORG_OUTDOOR, `id=${createdId}`);

      const opStealWrite = await call('POST', '/home/slide', { body: slideBody('越权写数码部', ORG_DIGITAL), token: OP_TOKEN });
      check('运营把轮播图写到别的店铺 → 拒绝', !opStealWrite.ok, opStealWrite.json?.message);

      // ================= D. 隔离效果 =================
      console.log('\n[D] 隔离效果（同一份数据、两个前台）');
      const outdoorAfter = await call('GET', '/home/slide/store', { storeCode: 'outdoor' });
      const defaultAfter = await call('GET', '/home/slide/store', { storeCode: 'default' });
      check('户外部前台看到刚新增的 1 张', slides(outdoorAfter).length === 1, `count=${slides(outdoorAfter).length}`);
      check('数码部前台仍是原来的 2 张（未被污染）', slides(defaultAfter).length >= 2, `count=${slides(defaultAfter).length}`);
    } else {
      console.log('\n[C] 运营账号用例：跳过（未提供 CK_OP_TOKEN）');
    }
  } finally {
    console.log('\n[E] 清理探针数据');
    if (createdId) {
      const del = await call('DELETE', '/home/slide/delete', { query: { id: createdId }, token: ADMIN_TOKEN });
      console.log(`  删除 ${createdId} → ${del.ok ? '成功' : `失败：${del.json?.message}`}`);
    } else {
      // 兜底：按描述找出探针残留并删除
      const all = await call('GET', '/home/slide/all', { token: ADMIN_TOKEN });
      for (const s of slides(all)) {
        if (s.desc === MARK) {
          const del = await call('DELETE', '/home/slide/delete', { query: { id: s.id }, token: ADMIN_TOKEN });
          console.log(`  兜底删除 ${s.id} → ${del.ok ? '成功' : `失败：${del.json?.message}`}`);
        }
      }
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

main().catch((e) => {
  console.error('探针异常：', e);
  process.exit(1);
});
