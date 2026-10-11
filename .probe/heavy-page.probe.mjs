/**
 * 重型页面 + DevTools 压力探针
 *
 * 流程：注入登录 token -> 逐个打开目标路由 -> 统计每个页面给 DevTools 的压力
 *      （请求数 / 脚本数 / DOM 节点数 / console 条数）-> 在最后一个页面连续刷新，
 *      观察第 2、3 次刷新是否卡住、DevTools 前端是否还能响应。
 *
 * 用法：
 *   $env:CDP_PORT=9334; $env:CK_TOKEN='<jwt>'; $env:PATHS='/dashboard/analysis,/shop/product'
 *   node .probe/heavy-page.probe.mjs
 */
const PORT = process.env.CDP_PORT || '9334';
const HTTP = `http://127.0.0.1:${PORT}`;
const TOKEN = process.env.CK_TOKEN;
const PATHS = (process.env.PATHS || '/dashboard/analysis,/shop/product').split(',').map((s) => s.trim()).filter(Boolean);
const RELOADS = Number(process.env.RELOADS || 3);
const BASE = 'http://127.0.0.1:3100';
if (!TOKEN) throw new Error('缺少 CK_TOKEN');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const json = async (p) => (await fetch(HTTP + p)).json();

class Cdp {
  constructor(ws, name) {
    this.ws = ws;
    this.name = name;
    this.seq = 0;
    this.pending = new Map();
    this.handlers = new Map();
    ws.addEventListener('message', (ev) => {
      const m = JSON.parse(ev.data);
      if (m.id && this.pending.has(m.id)) {
        const { resolve, reject, timer } = this.pending.get(m.id);
        clearTimeout(timer);
        this.pending.delete(m.id);
        m.error ? reject(new Error(JSON.stringify(m.error))) : resolve(m.result);
      } else if (m.method) {
        const h = this.handlers.get(m.method);
        h && h(m.params);
      }
    });
  }
  send(method, params = {}, timeout = 15000) {
    const id = ++this.seq;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`CDP timeout: ${method}`));
      }, timeout);
      this.pending.set(id, { resolve, reject, timer });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }
  on(m, f) {
    this.handlers.set(m, f);
  }
}

async function connect(wsUrl, name) {
  const ws = new WebSocket(wsUrl);
  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve, { once: true });
    ws.addEventListener('error', () => reject(new Error(`${name}: ws error`)), { once: true });
    setTimeout(() => reject(new Error(`${name}: timeout`)), 10000);
  });
  return new Cdp(ws, name);
}

const list = await json('/json/list');
const pageT = list.find((t) => t.type === 'page' && (t.url || '').includes('3100'));
const uiT = list.find((t) => (t.url || '').startsWith('devtools://'));
if (!pageT) throw new Error('未找到页面 target');
const page = await connect(pageT.webSocketDebuggerUrl, 'page');
const ui = uiT ? await connect(uiT.webSocketDebuggerUrl, 'devtools-ui') : null;
if (ui) await ui.send('Runtime.enable').catch(() => {});
console.log(`页面: ${pageT.url}`);
console.log(`DevTools 前端: ${ui ? '已打开 ✓' : '未打开'}`);

// ---------- 计数器 ----------
const c = { req: 0, fail: 0, console: 0, scripts: 0, paused: 0, exceptions: 0, loads: 0, navs: 0, wsFrames: 0 };
const samples = { console: [], fail: [], exception: [], pause: [] };
page.on('Page.loadEventFired', () => c.loads++);
page.on('Page.frameNavigated', (p) => {
  if (!p.frame.parentId) c.navs++;
});
page.on('Network.requestWillBeSent', () => c.req++);
page.on('Network.loadingFailed', (p) => {
  c.fail++;
  if (samples.fail.length < 10) samples.fail.push(`${p.errorText} ${p.type}`);
});
page.on('Network.webSocketFrameReceived', () => c.wsFrames++);
page.on('Runtime.consoleAPICalled', (p) => {
  c.console++;
  if (samples.console.length < 12) {
    samples.console.push(`${p.type}: ` + p.args.map((a) => a.value ?? a.description ?? a.type).join(' ').slice(0, 140));
  }
});
page.on('Runtime.exceptionThrown', (p) => {
  c.exceptions++;
  if (samples.exception.length < 8) {
    samples.exception.push((p.exceptionDetails?.exception?.description || p.exceptionDetails?.text || '').split('\n')[0].slice(0, 180));
  }
});
page.on('Debugger.scriptParsed', () => c.scripts++);
page.on('Debugger.paused', (p) => {
  c.paused++;
  const f = p.callFrames?.[0];
  if (samples.pause.length < 6) samples.pause.push(`${p.reason} @ ${f?.url || '?'}:${(f?.location?.lineNumber ?? 0) + 1}`);
});

for (const d of ['Page', 'Runtime', 'Network', 'Debugger', 'Log']) await page.send(`${d}.enable`).catch(() => {});

async function uiRtt(timeout = 1500) {
  if (!ui) return null;
  const t = Date.now();
  try {
    await ui.send('Runtime.evaluate', { expression: '1+1', returnByValue: true }, timeout);
    return Date.now() - t;
  } catch {
    return -1;
  }
}

async function evalPage(expr, timeout = 8000, awaitPromise = false) {
  const r = await page.send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise }, timeout);
  return r.result?.value;
}

// ---------- 1. 注入登录态 ----------
console.log('\n>>> 注入登录 token ...');
const injected = await evalPage(
  `(async () => {
     const auth = await import('/src/utils/auth/index.ts');
     const enums = await import('/src/enums/cacheEnum.ts');
     auth.setAuthCache(enums.TOKEN_KEY, ${JSON.stringify(TOKEN)});
     return 'token set';
   })()`,
  15000,
  true
).catch((e) => 'ERR: ' + e.message);
console.log('  ' + injected);

const report = [];
for (const path of PATHS) {
  const url = BASE + path;
  console.log(`\n>>> 打开 ${path}`);
  const snap = { req: c.req, fail: c.fail, console: c.console, scripts: c.scripts, loads: c.loads, navs: c.navs };
  const t0 = Date.now();
  await page.send('Page.navigate', { url }, 15000).catch((e) => console.log('  navigate 失败: ' + e.message));
  const ok = await (async () => {
    const t = Date.now();
    while (Date.now() - t < 30000) {
      if (c.loads > snap.loads) return true;
      await sleep(200);
    }
    return false;
  })();
  let loadMs = Date.now() - t0;
  // 等待页面稳定（异步模块 + 接口）
  await sleep(6000);

  const domNodes = await evalPage('document.querySelectorAll("*").length', 8000).catch(() => -1);
  const resCount = await evalPage('performance.getEntriesByType("resource").length', 8000).catch(() => -1);
  const heap = await evalPage('performance.memory ? Math.round(performance.memory.usedJSHeapSize/1048576) : -1', 8000).catch(() => -1);
  const curUrl = await evalPage('location.pathname + location.search', 8000).catch(() => '?');
  const t2 = Date.now();
  const rtt = await evalPage('1+1', 3000).catch(() => -1);
  const pageRtt = Date.now() - t2;
  const ur = await uiRtt();

  const rec = {
    path,
    gotLoad: ok,
    loadMs,
    landedOn: curUrl,
    domNodes,
    resCount,
    heapMB: heap,
    req: c.req - snap.req,
    fail: c.fail - snap.fail,
    console: c.console - snap.console,
    scripts: c.scripts - snap.scripts,
    pageRtt,
    uiRtt: ur,
  };
  report.push(rec);
  console.log(`  load事件: ${ok ? '✓' : '✗ 超时'}  耗时=${loadMs}ms  最终URL=${curUrl}`);
  console.log(`  DOM节点=${domNodes}  资源条目=${resCount}  JS堆=${heap}MB`);
  console.log(`  本次请求=${rec.req} 失败=${rec.fail} console=${rec.console} 新增脚本=${rec.scripts}`);
  console.log(`  页面主线程RTT=${pageRtt}ms  DevTools前端RTT=${ur === null ? 'n/a' : ur + 'ms'}`);
}

// ---------- 2. 在最后一个页面连续刷新 ----------
if (process.env.CACHE_DISABLED) {
  await page.send('Network.setCacheDisabled', { cacheDisabled: true });
  console.log('\n[已开启 Disable cache，模拟你 DevTools 里的设置]');
}
const last = PATHS[PATHS.length - 1];
console.log(`\n>>> 在 ${last} 上连续刷新 ${RELOADS} 次（复现"只能刷一次"）`);
const reloads = [];
for (let i = 1; i <= RELOADS; i++) {
  const s = { req: c.req, console: c.console, loads: c.loads, scripts: c.scripts };
  const t0 = Date.now();
  await page.send('Page.reload', { ignoreCache: false }, 15000).catch(() => {});
  const ok = await (async () => {
    const t = Date.now();
    while (Date.now() - t < 20000) {
      if (c.loads > s.loads) return true;
      await sleep(200);
    }
    return false;
  })();
  const ms = Date.now() - t0;
  const prtt = await evalPage('1+1', 3000).then(() => 1).catch(() => -1);
  const ur = await uiRtt();
  reloads.push({ round: i, ok, ms, req: c.req - s.req, console: c.console - s.console, scripts: c.scripts - s.scripts, uiRtt: ur });
  console.log(
    `  第 ${i} 次: ${ok ? 'OK ' : '卡死✗'} 耗时=${ms}ms 请求=${c.req - s.req} console=${c.console - s.console} 脚本=${c.scripts - s.scripts} 页面RTT=${prtt === -1 ? '超时✗' : 'ok'} DevToolsUI=${ur === null ? 'n/a' : ur + 'ms'}`
  );
  if (!ok) break;
  await sleep(1500);
}

console.log('\n================ 汇总 ================');
for (const r of report) {
  console.log(
    `${r.path.padEnd(24)} DOM=${String(r.domNodes).padStart(6)} 资源=${String(r.resCount).padStart(5)} 请求=${String(r.req).padStart(4)} 脚本=+${String(r.scripts).padStart(4)} console=${String(r.console).padStart(4)} WebSocket帧=${c.wsFrames}`
  );
}
console.log(`\ndebugger 暂停=${c.paused} 未捕获异常=${c.exceptions}`);
samples.pause.forEach((s) => console.log('  pause: ' + s));
samples.exception.forEach((s) => console.log('  ex: ' + s));
samples.fail.forEach((s) => console.log('  fail: ' + s));
console.log('\n--- console 样例 ---');
samples.console.forEach((s) => console.log('  ' + s));
process.exit(0);
