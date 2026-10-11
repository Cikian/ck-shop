/**
 * 重复刷新探针 —— 复现「打开 DevTools 后只能刷新一次」
 *
 * 用 CDP 连续触发多次 Page.reload，逐次记录：
 *   - 是否收到 load 事件（没收到 = 导航卡住，也就是用户看到的"刷新没反应"）
 *   - 本次刷新的请求数 / console 消息数 / 脚本数
 *   - 刷新后主线程是否还能响应
 *   - 是否命中 debugger 暂停
 *
 * 用法：
 *   $env:CDP_PORT=9334; $env:ROUNDS=5; node .probe/reload-repeat.probe.mjs
 */
const PORT = process.env.CDP_PORT || '9334';
const ROUNDS = Number(process.env.ROUNDS || 5);
const HTTP = `http://127.0.0.1:${PORT}`;
const LOAD_TIMEOUT = Number(process.env.LOAD_TIMEOUT || 25000);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const json = async (p) => (await fetch(HTTP + p)).json();

class Cdp {
  constructor(ws) {
    this.ws = ws;
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

async function connect(wsUrl) {
  const ws = new WebSocket(wsUrl);
  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve, { once: true });
    ws.addEventListener('error', () => reject(new Error('ws error')), { once: true });
    setTimeout(() => reject(new Error('ws open timeout')), 10000);
  });
  return new Cdp(ws);
}

const list = await json('/json/list');
const pageT = list.find((t) => t.type === 'page' && (t.url || '').includes('3100'));
const uiT = list.find((t) => (t.url || '').startsWith('devtools://'));
if (!pageT) throw new Error('未找到 3100 页面 target');
console.log(`页面: ${pageT.url}`);
console.log(`DevTools 前端: ${uiT ? '已打开 ✓（就是 F12 的状态）' : '未打开'}`);
console.log(`DEBUGGER_DOMAIN=${process.env.NO_DEBUGGER ? '不启用' : '启用（等价 DevTools Sources）'}\n`);

const page = await connect(pageT.webSocketDebuggerUrl);

const c = {
  loads: 0,
  navs: 0,
  req: 0,
  reqFail: 0,
  reqSample: [],
  console: 0,
  consoleSample: [],
  exceptions: 0,
  exceptionSample: [],
  scripts: 0,
  paused: 0,
  pauseSample: [],
};
page.on('Page.loadEventFired', () => c.loads++);
page.on('Page.frameNavigated', (p) => {
  if (!p.frame.parentId) c.navs++;
});
page.on('Network.requestWillBeSent', (p) => {
  c.req++;
  if (c.reqSample.length < 400) c.reqSample.push(p.request.url);
});
page.on('Network.loadingFailed', () => c.reqFail++);
page.on('Runtime.consoleAPICalled', (p) => {
  c.console++;
  if (c.consoleSample.length < 15) {
    c.consoleSample.push(`${p.type}: ` + p.args.map((a) => a.value ?? a.description ?? a.type).join(' ').slice(0, 150));
  }
});
page.on('Runtime.exceptionThrown', (p) => {
  c.exceptions++;
  if (c.exceptionSample.length < 10) {
    c.exceptionSample.push((p.exceptionDetails?.exception?.description || p.exceptionDetails?.text || '').split('\n')[0].slice(0, 180));
  }
});
page.on('Debugger.scriptParsed', () => c.scripts++);
page.on('Debugger.paused', (p) => {
  c.paused++;
  if (c.pauseSample.length < 10) {
    const f = p.callFrames?.[0];
    c.pauseSample.push(`${p.reason} @ ${f?.url || '?'}:${(f?.location?.lineNumber ?? 0) + 1} ${(f?.functionName || '').slice(0, 60)}`);
  }
});

await page.send('Page.enable');
await page.send('Runtime.enable');
await page.send('Network.enable');
await page.send('Log.enable').catch(() => {});
if (!process.env.NO_DEBUGGER) await page.send('Debugger.enable');

async function waitFor(fn, timeout) {
  const t = Date.now();
  while (Date.now() - t < timeout) {
    if (fn()) return true;
    await sleep(100);
  }
  return false;
}

const results = [];
for (let i = 1; i <= ROUNDS; i++) {
  const loads0 = c.loads;
  const navs0 = c.navs;
  const req0 = c.req;
  const con0 = c.console;
  const reqSample0 = c.reqSample.length;

  console.log(`──────── 第 ${i} 次刷新 ────────`);
  const t0 = Date.now();
  let sendErr = null;
  try {
    await page.send('Page.reload', { ignoreCache: false }, 15000);
  } catch (e) {
    sendErr = e.message;
  }
  const gotLoad = await waitFor(() => c.loads > loads0, LOAD_TIMEOUT);
  const gotNav = c.navs > navs0;
  const ms = Date.now() - t0;

  // 刷新后主线程是否还能响应
  let rtt = -1;
  try {
    const t = Date.now();
    await page.send('Runtime.evaluate', { expression: '1+1', returnByValue: true }, 3000);
    rtt = Date.now() - t;
  } catch {
    rtt = -1;
  }

  const rec = {
    round: i,
    ms,
    gotLoad,
    gotNav,
    rtt,
    req: c.req - req0,
    console: c.console - con0,
    newReqSample: c.reqSample.slice(reqSample0, reqSample0 + 12),
    sendErr,
  };
  results.push(rec);
  console.log(`  load 事件: ${gotLoad ? '收到 ✓' : `未收到 ✗（等待 ${ms}ms 超时）`}   frameNavigated: ${gotNav}`);
  console.log(`  本次请求数: ${rec.req}   console: ${rec.console}   主线程 RTT: ${rtt === -1 ? '超时 ✗' : rtt + 'ms'}`);
  if (!gotLoad) {
    console.log(`  >>> 复现！第 ${i} 次刷新卡住，页面没有完成导航（这就是"刷新没反应"）`);
    console.log('  该次刷新最早发出的请求:');
    rec.newReqSample.forEach((u) => console.log('    ' + u.slice(0, 140)));
    break;
  }
  await sleep(1500);
}

console.log('\n================ 汇总 ================');
for (const r of results) {
  console.log(
    `第 ${r.round} 次: ${r.gotLoad ? 'OK ' : '卡死'} 耗时=${String(r.ms).padStart(6)}ms 请求=${String(r.req).padStart(4)} console=${String(r.console).padStart(4)} rtt=${r.rtt}ms`
  );
}
console.log(`\ndebugger 暂停次数: ${c.paused}`);
if (c.pauseSample.length) c.pauseSample.forEach((s) => console.log('  ' + s));
console.log(`脚本解析总数: ${c.scripts}`);
console.log(`未捕获异常: ${c.exceptions}`);
c.exceptionSample.forEach((s) => console.log('  ' + s));
console.log('\n--- console 样例 ---');
c.consoleSample.forEach((s) => console.log('  ' + s));
process.exit(0);
