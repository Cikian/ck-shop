/**
 * 从本地 Redis 里取出当前登录 token（沿用 .probe/README.md 里的既有做法）
 * 只做只读 SCAN / GET / TTL，不写任何键。
 */
import net from 'node:net';

const HOST = process.env.REDIS_HOST || '127.0.0.1';
const PORT = Number(process.env.REDIS_PORT || 6379);

function encode(args) {
  const parts = [Buffer.from(`*${args.length}\r\n`)];
  for (const a of args) {
    const s = Buffer.from(String(a));
    parts.push(Buffer.from(`$${s.length}\r\n`), s, Buffer.from('\r\n'));
  }
  return Buffer.concat(parts);
}

function parse(buf, pos) {
  if (pos >= buf.length) return null;
  const t = String.fromCharCode(buf[pos]);
  const nl = buf.indexOf('\r\n', pos);
  if (nl === -1) return null;
  const line = buf.toString('utf8', pos + 1, nl);
  const next = nl + 2;
  if (t === '+') return [line, next];
  if (t === '-') return [{ error: line }, next];
  if (t === ':') return [Number(line), next];
  if (t === '$') {
    const len = Number(line);
    if (len === -1) return [null, next];
    if (buf.length < next + len + 2) return null;
    return [buf.toString('utf8', next, next + len), next + len + 2];
  }
  if (t === '*') {
    const n = Number(line);
    if (n === -1) return [null, next];
    let p = next;
    const arr = [];
    for (let i = 0; i < n; i++) {
      const r = parse(buf, p);
      if (!r) return null;
      arr.push(r[0]);
      p = r[1];
    }
    return [arr, p];
  }
  return null;
}

const sock = net.createConnection({ host: HOST, port: PORT });
await new Promise((res, rej) => {
  sock.once('connect', res);
  sock.once('error', rej);
});

let buf = Buffer.alloc(0);
const waiters = [];
sock.on('data', (d) => {
  buf = Buffer.concat([buf, d]);
  for (;;) {
    const r = parse(buf, 0);
    if (!r) break;
    buf = buf.subarray(r[1]);
    if (waiters.length) waiters.shift()(r[0]);
  }
});

function cmd(...args) {
  return new Promise((resolve, reject) => {
    waiters.push((v) => (v && v.error ? reject(new Error(v.error)) : resolve(v)));
    sock.write(encode(args));
  });
}

const keys = [];
let cursor = '0';
do {
  const [next, batch] = await cmd('SCAN', cursor, 'MATCH', '*token*', 'COUNT', 1000);
  cursor = next;
  if (batch) keys.push(...batch);
} while (cursor !== '0');

console.log(`匹配到 ${keys.length} 个 token 相关键：`);
const found = [];
for (const k of keys) {
  const ttl = await cmd('TTL', k);
  const v = await cmd('GET', k);
  const isJwt = typeof v === 'string' && v.split('.').length === 3;
  console.log(`  ${k}   ttl=${ttl}s  jwt=${isJwt ? 'yes' : 'no'}  len=${v ? v.length : 0}`);
  if (isJwt) found.push({ key: k, value: v, ttl });
}
if (found.length) {
  console.log(`\n找到 ${found.length} 个 JWT。最像当前登录态的是 ttl 最大的那个。`);
  found.sort((a, b) => b.ttl - a.ttl);
  console.log(`\nCK_TOKEN=${found[0].value}`);
} else {
  console.log('\n没有找到 JWT 形式的 token（可能当前没有登录态）。');
}
sock.end();
process.exit(0);
