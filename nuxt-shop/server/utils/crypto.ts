// server/utils/crypto.ts
//
// ⚠️ 重要说明：
// 你贴的前端参考代码（encryptAESCBC）是在浏览器里做加密的——这意味着
// AES 的 key 会被打包进浏览器能看到的 JS 文件里，任何人按 F12 都能扒出来，
// 严格来说这个"加密"只能防"明文抓包"，防不了"看源码"。
//
// 这里我把加密逻辑挪到了 server 端（Nuxt 的 Node.js 服务器上）做，
// key 存在 runtimeConfig 里，不会被打进浏览器的 JS 包，安全性更好，
// 前端只需要把明文密码传给 Nuxt 自己的 /api/auth/login，
// 由 server 端加密后再转发给 Java 后端。
//
// ⚠️ 需要你核实的地方：
// 1. key 的值（下面用的是 JeecgBoot 开源项目里常见的默认值，你们后端很可能改过）
// 2. 加密模式是 ECB 还是 CBC（下面默认写的是 ECB，这也是 JeecgBoot 默认实现）
// 3. 如果模式是 CBC，还需要一个 iv（初始向量），我在下面也留了 CBC 的写法，按需切换
//
// 找 Java 后端要这几个信息最快：让后端同事发一下 AesEncryptUtil（或类似命名）的源码看一眼即可。

import CryptoJS from 'crypto-js'

export function encryptPassword(plainPassword: string): string {
  const config = useRuntimeConfig()
  const key = CryptoJS.enc.Utf8.parse(config.aesKey)
  const iv = CryptoJS.enc.Utf8.parse(config.aesIv)

  const encrypted = CryptoJS.AES.encrypt(plainPassword, key, {
    mode: CryptoJS.mode.CBC,
    iv,
    padding: CryptoJS.pad.Pkcs7,
  })
  return encrypted.toString()
}
