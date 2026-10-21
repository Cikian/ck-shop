# Google 登录失败（回调页报“后端服务响应超时”）排查与修复

> 现象：用户在 `/en/auth/google/callback`（或 `/zh/...`）看到 **“Google sign-in failed / 后端服务响应超时，请稍后重试”**，重试也一直失败。
>
> 结论先说：**不是 OAuth 代码写错了，也不是 Google Client ID 配错了**（能跳到 Google 并带 code 回来，说明授权环节是通的）。真正失败在**后端拿着 code 去 Google 换 token / 取用户信息这一步**——后端到 Google 的网络（本机走 Clash 代理）时快时慢，偶发 10 秒以上卡死；同时 BFF 到后端的超时也是 10 秒，BFF 先超时，把后端真实的失败原因盖成了“后端服务响应超时”。

## 一、证据链

### 1. 后端日志（`logs/2026-10-18/begin-at_05.01.18_0.log`）

```text
2026-10-18 06:01:06.377 [http-nio-9951-exec-5] WARN  o.j.m.shop.account.service.ShopGoogleOAuthService
  - 获取 Google 用户信息失败，网络异常：Read timed out
2026-10-18 06:01:06.397 [http-nio-9951-exec-5] ERROR o.jeecg.common.exception.JeecgBootExceptionHandler
  - 获取 Google 用户信息失败：网络异常，请重试或改用邮箱登录
```

即：token 交换已经成功，**卡在 `openidconnect.googleapis.com/v1/userinfo` 的读取超时（10s）**。
页面上的“后端服务响应超时，请稍后重试”只出现在 `nuxt-shop/server/utils/apiClient.ts` 的 `onRequestError` 里，说明 BFF 等后端超过 10s 先放弃了。

### 2. 历史日志里的同类失败（同一根因，不同表现）

| 时间 | 日志 | 失败点 |
| --- | --- | --- |
| 2026-10-16 09:22 / 09:23 | `换取 Google access_token 失败，网络异常：Read timed out` | token 交换 |
| 2026-10-16 23:04 | `获取 Google 用户信息失败，网络异常：Remote host terminated the handshake` | userinfo |
| 2026-10-18 06:01 | `获取 Google 用户信息失败，网络异常：Read timed out` | userinfo |
| 2026-10-15 20:46 | `Google 登录自动注册新用户，email=...` | 成功 |

说明是**后端出网到 Google 的链路不稳定**，不是每次必挂、也不是账号/回调地址问题。

### 3. 本机网络实测

- 直连 Google：`oauth2.googleapis.com`、`openidconnect.googleapis.com`、`accounts.google.com` 的 443 **连不上**（SYN 被丢，curl 12s 超时）。
- 系统代理（Clash `127.0.0.1:7890`）：能通，但延迟在 **0.2s ～ 15s** 之间大幅波动，偶尔直接读超时。
- 后端进程（PID 28792）在 Google 回调期间确实有一条指向 `127.0.0.1:7890` 的 established 连接 → **后端 JVM 走的是系统代理**（`-Djava.net.useSystemProxies=true` 或等效代理参数），浏览器可以正常访问 Google 也是因为走了同一个代理。

### 4. 超时预算错配（放大器）

| 环节 | 原配置 | 问题 |
| --- | --- | --- |
| 后端 OkHttp 单次调用 | connect 5s / read 10s | Google 偶发 >10s 就被误杀 |
| BFF → 后端 | 10s（全局 `API_TIMEOUT_MS`） | 后端还没等到 Google 结果，BFF 先超时 |
| 结果 | 用户看到 504 “后端服务响应超时” | 真正原因被掩盖；且 `state` 已被后端消费，点“重试”必然报“登录已过期/链接已被使用” |

## 二、已做的代码修复

### 1. 后端：用 id_token 替代第二次 userinfo 请求

`ck-server/jeecg-boot-module/ck-shop/.../ShopGoogleOAuthService.java`

- token 端点本来就会返回 `id_token`（scope 含 `openid`），其 `sub / email / name / picture` 与 userinfo 是同一批声明；
- 新增 `parseIdToken()`：校验 `iss`、`aud`（本应用 client_id）、`exp`、`sub` 后直接产出用户信息；
- `exchangeCode()` 返回 `accessToken + idToken`，`loginByGoogle()` 优先用 id_token，**只有 id_token 缺失/校验不过才回退 userinfo**（保留兜底）。

效果：正常登录从 **2 次跨境请求降到 1 次**，直接消除了 06:01、10-16 23:04 这类“userinfo 卡死”失败。

> 安全性：按 OpenID Connect Core 3.1.3.7，id_token 通过 TLS 直接从 token 端点取得时，可用 TLS 服务端校验替代 JWT 签名校验；`iss/aud/exp/sub` 校验仍必须保留（已实现）。

### 2. 后端：超时按“墙内代理”现实放宽并封顶

- 主路径（token 交换）：`connectTimeout` 5s、`readTimeout` 20s、`writeTimeout` 10s；
- 新增 `callTimeout(25s)`：限制单次请求（DNS + 建连 + TLS + 读取）总时长；
- userinfo 兜底路径单独用 4s 封顶的客户端（正常走不到这里），
  保证“最坏 25s + 4s = 29s，仍小于 BFF 的 30s”；
- 25s/29s < BFF 30s，保证**后端总能先返回真实错误**，而不是被 BFF 超时盖住。

### 3. BFF：Google 回调单独放宽到 30s，并给出可操作文案

`nuxt-shop/server/utils/apiClient.ts`、`nuxt-shop/server/api/auth/google/callback.post.ts`

- `createApiClient()` 新增可选 `timeoutMs`（普通接口仍是默认 10s，不受影响）；
- Google 回调使用 30s；
- 若仍发生 504，提示改为：“服务器访问 Google 超时，本次登录凭证已作废，请返回登录页重新发起 Google 登录”，不再误导用户点“重试”。

## 三、本机/部署环境要做的事（真正决定成败）

代码只是容错与提速，**Google 登录能否稳定，取决于后端出网质量**：

1. **给 Clash 换一个稳定的节点**，或为以下域名单独指定低延迟节点：
   - `oauth2.googleapis.com`
   - `openidconnect.googleapis.com`
   - `accounts.google.com`
2. 验证后端这条链路（在跑后端的机器上执行，`%{time_total}` 最好稳定 < 3s）：

   ```bash
   curl -x http://127.0.0.1:7890 -o /dev/null -s \
     -w "userinfo: %{http_code} %{time_total}s\n" \
     -H "Authorization: Bearer fake" \
     https://openidconnect.googleapis.com/v1/userinfo
   ```

   连续执行 5 次，若有明显长尾（>10s）或失败，说明代理节点/规则仍需调整。
3. 生产环境：服务器应能**直连 Google**（境外部署或配置稳定的出网代理），不要把“墙内 + 单机 Clash”当作生产链路。
4. 后端 JVM 若依赖系统代理，确保启动参数里有 `-Djava.net.useSystemProxies=true`（或显式的 `-Dhttps.proxyHost/-Dhttps.proxyPort`）。可在 IDEA 的 Run Configuration → VM options 里加。
5. 重启后端后验证：日志里登录成功应出现 `Google 登录自动注册新用户` / `Google 账号绑定到既有用户`；失败也会打印具体是 token 还是 userinfo 环节。

## 四、改动文件清单

| 文件 | 改动 |
| --- | --- |
| `ck-server/.../service/ShopGoogleOAuthService.java` | 超时调整 + `parseIdToken()` + `exchangeCode()` 返回 `TokenResult` |
| `ck-server/.../service/impl/ShopAccountServiceImpl.java` | 优先用 id_token，失败回退 userinfo |
| `nuxt-shop/server/utils/apiClient.ts` | `createApiClient` 支持 `timeoutMs` |
| `nuxt-shop/server/api/auth/google/callback.post.ts` | 回调超时 30s + 504 可操作文案 |
| `docs/Google登录失败-排查与修复.md` | 本文档 |

> 注意：后端是 Java 编译产物，**改完必须重启后端**才生效；Nuxt BFF 在 dev 模式下会热更新。
