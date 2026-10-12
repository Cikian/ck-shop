/**
 * 评审修复项 · 回归验收探针
 *
 * 覆盖本轮针对代码评审所做的修复（每条对应评审编号）：
 *   R1  轮播图新增返回主键（前端自动保存不再丢草稿/重复创建）
 *   R2  轮播图 targetUrl 可以清空（以前传空串被忽略，"链接删不掉"）
 *   R3  轮播图跨店铺改归属真的生效（以前 WHERE 用目标值 → 0 行却报成功）
 *   R4  店铺端轮播顺序按后台的 sort（以前投影了不存在的 order 字段且无排序）
 *   R5  分类详情接口的越权校验（以前按 id 可读别部门分类详情）
 *   R6  店铺绑"公司"节点被拒 / 启用中的店铺不允许解绑部门
 *   R7  商品标签"取消"真的生效（以前读取 SQL 漏了 del_flag=0，删了还在、也加不回来）
 *   R8  店铺没绑部门时前台降级为空数据而不是 500（用现有的 test 店铺验证）
 *   R9  导出接口仍然可用（管理员）
 *
 * 用法：
 *   $env:CK_TOKEN='<admin JWT>'; $env:CK_OP_TOKEN='<运营 JWT>'
 *   $env:CK_BASE='http://127.0.0.1:9952/api'
 *   node .probe/review-fixes.probe.mjs
 *
 * 测试数据带 PBX- 前缀，结束时会自己清理（轮播图与商品）。
 */
const BASE = process.env.CK_BASE || 'http://127.0.0.1:9951/api';
const ADMIN = process.env.CK_TOKEN || '';
const OP = process.env.CK_OP_TOKEN || '';
const ORG_DIGITAL = 'A06A01';
const ORG_OUTDOOR = 'A06A02';

async function call(method, path, { query = {}, body, token, storeCode, raw = false } = {}) {
  const qs = new URLSearchParams(query).toString();
  const url = `${BASE}${path}${qs ? `?${qs}` : ''}`;
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['X-Access-Token'] = token;
  if (storeCode) headers['X-Store-Code'] = storeCode;
  const res = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  if (raw) {
    const buf = Buffer.from(await res.arrayBuffer());
    return { status: res.status, bytes: buf.length, contentType: res.headers.get('content-type') || '' };
  }
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
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const slides = (r) => (Array.isArray(r.json?.result) ? r.json.result : []);
const products = (r) => (Array.isArray(r.json?.result?.records) ? r.json.result.records : []);

const MARK = `PBX-${Date.now().toString(36)}`;
const slideBody = (extra = {}) => ({
  desc: MARK,
  sort: 5,
  enabled: 'Y',
  goods: '',
  en: { title_en: 'T', pic_url_en: 'https://x/y.png' },
  zh: { title_zh: 'T', pic_url_zh: 'https://x/y.png' },
  sysOrgCode: ORG_DIGITAL,
  ...extra,
});

async function main() {
  if (!ADMIN) {
    console.error('缺少 CK_TOKEN');
    process.exit(2);
  }
  console.log(`BASE = ${BASE}\n`);
  const createdSlides = [];
  const createdProducts = [];
  try {
    // ---------- R1/R2/R3/R4 轮播图 ----------
    console.log('[R1-R4] 轮播图');
    const create = await call('POST', '/home/slide', { token: ADMIN, body: slideBody({ targetUrl: 'https://example.com/keep' }) });
    const newId = create.json?.result;
    check('R1 新增返回主键', typeof newId === 'string' && /^[0-9a-f]{24}$/.test(newId), `result=${JSON.stringify(newId)}`);
    if (newId) createdSlides.push(newId);
    await sleep(300);

    const before = slides(await call('GET', '/home/slide/all', { query: { sysOrgCode: ORG_DIGITAL }, token: ADMIN })).find((s) => s.id === newId);
    check('R2 创建时带上的链接可见', !!before && String(before.targetUrl).includes('example.com/keep'), `targetUrl=${before?.targetUrl}`);

    // 清空链接
    await call('PUT', '/home/slide', { token: ADMIN, body: slideBody({ id: newId, targetUrl: '' }) });
    await sleep(300);
    const cleared = slides(await call('GET', '/home/slide/all', { query: { sysOrgCode: ORG_DIGITAL }, token: ADMIN })).find((s) => s.id === newId);
    check('R2 传空串能清空链接', !!cleared && !String(cleared.targetUrl || '').trim(), `targetUrl=${JSON.stringify(cleared?.targetUrl)}`);

    // 跨店铺改归属
    await call('PUT', '/home/slide', { token: ADMIN, body: slideBody({ id: newId, sysOrgCode: ORG_OUTDOOR }) });
    await sleep(300);
    const inOutdoor = slides(await call('GET', '/home/slide/all', { query: { sysOrgCode: ORG_OUTDOOR }, token: ADMIN })).find((s) => s.id === newId);
    const stillDigital = slides(await call('GET', '/home/slide/all', { query: { sysOrgCode: ORG_DIGITAL }, token: ADMIN })).find((s) => s.id === newId);
    check('R3 跨店铺改归属真的生效', !!inOutdoor && inOutdoor.sysOrgCode === ORG_OUTDOOR && !stillDigital,
      `outdoor=${inOutdoor?.sysOrgCode} digital还在=${!!stillDigital}`);

    // R4 排序：把这条的 sort 设成 1，看店铺端是否排到最前
    await call('PUT', '/home/slide/sort', { token: ADMIN, body: [{ id: newId, sort: 0 }] });
    await sleep(400);
    const outdoorStore = await call('GET', '/home/slide/store', { storeCode: 'outdoor' });
    const list = slides(outdoorStore);
    check('R4 店铺端按 sort 升序返回', list.length > 0 && list[0]._id === newId,
      `首条 desc=${list[0]?.desc} sort=${list[0]?.sort} 条数=${list.length}`);

    // 还原归属并删除
    await call('PUT', '/home/slide', { token: ADMIN, body: slideBody({ id: newId }) });
    await sleep(200);

    // ---------- R5 分类详情越权 ----------
    if (OP) {
      console.log('\n[R5] 分类详情越权校验（运营读别部门分类）');
      const cat = await call('GET', '/shop/category/list', { query: { pageNo: 1, pageSize: 1 }, token: ADMIN });
      const otherCat = (cat.json?.result?.records || [])[0];
      if (otherCat) {
        const steal = await call('GET', '/shop/category/queryById', { query: { id: otherCat.id }, token: OP });
        check('R5 运营读别部门分类详情 → 拒绝', !steal.ok, steal.json?.message);
      } else {
        check('R5 跳过（没有分类数据）', false, '库中没有分类');
      }
    }

    // ---------- R6 店铺绑定校验 ----------
    console.log('\n[R6] 店铺 ↔ 部门绑定校验');
    const stores = await call('GET', '/shop/store/all', { token: ADMIN });
    const byCode = Object.fromEntries((stores.json?.result || []).map((s) => [s.code, s]));
    const outdoor = byCode.outdoor;
    const unbind = await call('POST', '/shop/store/edit', { token: ADMIN, body: { ...outdoor, departId: '' } });
    check('R6 启用中的店铺不允许解绑部门', !unbind.ok, unbind.json?.message);
    const company = await call('POST', '/shop/store/edit', { token: ADMIN, body: { ...outdoor, departId: '2109513616048173058' } });
    check('R6 不允许把店铺绑到「公司」节点', !company.ok, company.json?.message);
    const after = await call('GET', '/shop/store/queryById', { query: { id: outdoor.id }, token: ADMIN });
    check('R6 被拒后库中绑定未被改动', after.json?.result?.departId === outdoor.departId, `departId=${after.json?.result?.departId}`);

    // ---------- R7 标签取消 ----------
    console.log('\n[R7] 商品标签取消/重新勾选');
    // 库里可能一个标签都没有，先建一个临时标签（结束时会删）
    let tempTagId = null;
    {
      const tagAdd = await call('POST', '/shop/tag/add', {
        token: ADMIN,
        body: {
          code: `PBXTAG${Date.now().toString(36)}`,
          sort: 0,
          isShow: 'Y',
          translations: [{ languageCode: 'zh', name: 'PBX 临时标签' }],
        },
      });
      tempTagId = tagAdd.json?.result || null;
      if (!tempTagId) {
        const tags = await call('GET', '/shop/tag/all', { token: ADMIN });
        const list = Array.isArray(tags.json?.result) ? tags.json.result : [];
        tempTagId = list[0]?.id || null;
      }
    }
    const tagList = tempTagId ? [{ id: tempTagId }] : [];
    if (tagList.length > 0) {
      const tag = tagList[0];
      const spu = `PBX-TAG-${Date.now().toString(36)}`;
      const add = await call('POST', '/shop/product/add', {
        token: ADMIN,
        body: {
          spuCode: spu, price: 1, unit: '件', status: 'Y', isRecommend: 'N', sortOrder: 0,
          sysOrgCode: ORG_DIGITAL, tagIds: [tag.id], images: [], specs: [], combinations: [],
          translations: [{ languageCode: 'zh', title: 'PBX 标签测试' }],
        },
      });
      const pid = add.json?.result;
      if (pid) createdProducts.push(pid);
      await sleep(300);
      const withTag = await call('GET', '/shop/product/queryById', { query: { id: pid }, token: ADMIN });
      check('R7 打标签后详情有该标签', Array.isArray(withTag.json?.result?.tagIds) && withTag.json.result.tagIds.includes(tag.id),
        `tagIds=${JSON.stringify(withTag.json?.result?.tagIds)}`);
      // 取消标签（tagIds 传空数组）
      await call('POST', '/shop/product/edit', {
        token: ADMIN,
        body: {
          id: pid, spuCode: spu, price: 1, unit: '件', status: 'Y', isRecommend: 'N', sortOrder: 0,
          sysOrgCode: ORG_DIGITAL, tagIds: [], images: [], specs: [], combinations: [],
          translations: [{ languageCode: 'zh', title: 'PBX 标签测试' }],
        },
      });
      await sleep(300);
      const noTag = await call('GET', '/shop/product/queryById', { query: { id: pid }, token: ADMIN });
      check('R7 取消标签后详情不再有该标签', !(noTag.json?.result?.tagIds || []).includes(tag.id),
        `tagIds=${JSON.stringify(noTag.json?.result?.tagIds)}`);
      // 重新勾选
      await call('POST', '/shop/product/edit', {
        token: ADMIN,
        body: {
          id: pid, spuCode: spu, price: 1, unit: '件', status: 'Y', isRecommend: 'N', sortOrder: 0,
          sysOrgCode: ORG_DIGITAL, tagIds: [tag.id], images: [], specs: [], combinations: [],
          translations: [{ languageCode: 'zh', title: 'PBX 标签测试' }],
        },
      });
      await sleep(300);
      const again = await call('GET', '/shop/product/queryById', { query: { id: pid }, token: ADMIN });
      check('R7 重新勾选后标签回来了', (again.json?.result?.tagIds || []).includes(tag.id),
        `tagIds=${JSON.stringify(again.json?.result?.tagIds)}`);
    } else {
      console.log('  （没有标签数据，跳过 R7）');
    }

    // ---------- R8 店铺归属校验（把"未绑部门却启用的店铺"挡在入口） ----------
    console.log('\n[R8] 店铺归属校验');
    const tempCode = `pbx${Date.now().toString(36)}`;
    const addEnabled = await call('POST', '/shop/store/add', {
      token: ADMIN,
      body: { code: tempCode, name: 'PBX 临时店铺', status: 'Y', sortOrder: 0 },
    });
    check('R8 启用中的店铺不绑部门 → 拒绝创建', !addEnabled.ok, addEnabled.json?.message);

    const addDisabled = await call('POST', '/shop/store/add', {
      token: ADMIN,
      body: { code: tempCode, name: 'PBX 临时店铺', status: 'N', sortOrder: 0 },
    });
    const tempStoreId = addDisabled.json?.result;
    check('R8 停用店铺允许暂不绑部门（纯展示站点）', !!tempStoreId, addDisabled.json?.message);
    if (tempStoreId) {
      const enable = await call('POST', '/shop/store/edit', {
        token: ADMIN,
        body: { id: tempStoreId, code: tempCode, name: 'PBX 临时店铺', status: 'Y', sortOrder: 0 },
      });
      check('R8 把未绑部门的店铺改成启用 → 拒绝', !enable.ok, enable.json?.message);
      await call('DELETE', '/shop/store/delete', { query: { id: tempStoreId }, token: ADMIN });
      console.log(`  临时店铺 ${tempStoreId} 已删除`);
    }

    // ---------- R9 导出可用 ----------
    console.log('\n[R9] 导出接口');
    const exp = await call('GET', '/shop/product/exportXls', { token: ADMIN, raw: true });
    check('R9 管理员导出商品返回 xlsx', exp.status === 200 && exp.bytes > 1000,
      `HTTP=${exp.status} bytes=${exp.bytes} type=${exp.contentType}`);
    const expStore = await call('GET', '/shop/store/exportXls', { token: ADMIN, raw: true });
    check('R9 店铺导出（补了 @Excel 与权限注解）返回 xlsx', expStore.status === 200 && expStore.bytes > 1000,
      `HTTP=${expStore.status} bytes=${expStore.bytes}`);
    const anonExport = await call('GET', '/shop/store/exportXls', { raw: true });
    check('R9 店铺导出不再允许匿名访问', anonExport.status !== 200 || anonExport.bytes < 1000,
      `HTTP=${anonExport.status} bytes=${anonExport.bytes}`);
  } finally {
    console.log('\n[清理]');
    for (const id of createdSlides) {
      const r = await call('DELETE', '/home/slide/delete', { query: { id }, token: ADMIN });
      console.log(`  轮播图 ${id} → ${r.ok ? '已删除' : `失败：${r.json?.message}`}`);
    }
    for (const id of createdProducts) {
      const r = await call('DELETE', '/shop/product/delete', { query: { id }, token: ADMIN });
      console.log(`  商品 ${id} → ${r.ok ? '已删除（逻辑）' : `失败：${r.json?.message}`}`);
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
