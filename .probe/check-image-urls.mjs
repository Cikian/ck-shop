/**
 * 候选图片 URL 可用性核验：只保留真正能打开的公开图片。
 * 用法：node .probe/check-image-urls.mjs
 */
const CANDIDATES = {
  数码: {
    phone: [
      '1511707171634-5f897ff02aa9', '1592750475338-74b7b21085ab', '1580910051074-3eb694886505',
      '1520923642038-b4259acecbd7', '1512499617640-c74ae3a79d37', '1567581935884-3349723552ca',
    ],
    headphone: [
      '1505740420928-5e560c06d30e', '1583394838336-acd977736f90', '1546435770-a3e426bf472b', '1484704849700-f032a568e944',
    ],
    laptop: [
      '1517336714731-489689fd1ca8', '1496181133206-80ce9b88a853', '1541807084-5c52b6b3adef', '1587614382346-4ec70e388b28',
    ],
    camera: ['1526170375885-4d8ecf77b99f', '1502920917128-1aa500764cbd', '1495707902641-75cac588d2e9'],
    watch: ['1523275335684-37898b6baf30', '1546868871-7041f2a55e12', '1524592094714-0f0654e20314'],
    speaker: ['1608043152269-423dbba4e7e1', '1545454675-3531b543be5d'],
    keyboard: ['1587829741301-dc798b83add3', '1527864550417-7fd91fc51a46'],
    tablet: ['1544244015-0df4b3ffc6b0', '1561154464-82e9adf32764'],
  },
  户外: {
    tent: ['1504280390367-361c6d9f38f4', '1478131143081-80f7f84ca84d', '1537565266759-34bbc16be345', '1445308394109-4ec2920981b1'],
    backpack: ['1553062407-98eeb64c6a62', '1622260614153-03223fb72052', '1509763572825-c891adcaed4b'],
    shoes: ['1542291026-7eec264c27ff', '1600185365483-26d7a4cc7519', '1552346154-21d32810aba3', '1460353581641-37baddab0fa2'],
    jacket: ['1551028719-00167b16eac5', '1544923246-77307dd654cb', '1591047139829-d91aecb6caea'],
    gear: ['1510312305653-8ed496efae75', '1508873881324-c92a3fc536ba', '1551632811-561732d1e306', '1602143407151-7111542de6e8'],
    bottle: ['1602143407151-7111542de6e8', '1523362628745-0c100150b504'],
    hiking: ['1533240332313-0db49b459ad6', '1551632811-561732d1e306', '1454391304352-2bf4678b1a7a'],
  },
};

const urlOf = (id, w = 900, h = 900) =>
  `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${w}&h=${h}&q=80`;

async function probe(url) {
  try {
    const res = await fetch(url, { method: 'GET', headers: { 'User-Agent': 'Mozilla/5.0' } });
    const type = res.headers.get('content-type') || '';
    const len = Number(res.headers.get('content-length') || 0);
    if (!res.ok) return { ok: false, why: `HTTP ${res.status}` };
    if (!type.startsWith('image/')) return { ok: false, why: `content-type=${type}` };
    if (len && len < 2000) return { ok: false, why: `too small ${len}` };
    const buf = Buffer.from(await res.arrayBuffer());
    return { ok: true, bytes: buf.length, type };
  } catch (e) {
    return { ok: false, why: e.message };
  }
}

const good = [];
const bad = [];
for (const [group, kinds] of Object.entries(CANDIDATES)) {
  for (const [kind, ids] of Object.entries(kinds)) {
    for (const id of ids) {
      const url = urlOf(id);
      const r = await probe(url);
      if (r.ok) {
        good.push({ group, kind, id, url });
        console.log(`OK    ${group}/${kind}  ${id}  ${(r.bytes / 1024).toFixed(0)}KB`);
      } else {
        bad.push({ group, kind, id, why: r.why });
        console.log(`FAIL  ${group}/${kind}  ${id}  ${r.why}`);
      }
    }
  }
}
console.log(`\n可用 ${good.length} / 共 ${good.length + bad.length}`);
console.log(JSON.stringify(good, null, 0));
