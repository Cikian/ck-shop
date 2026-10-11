# 后端诊断探针

本目录下的脚本用于**在没有 Maven、没有浏览器的情况下直接验证后端接口**。
本次开发中它们定位到了"保存后提示未找到数据"的真正原因（`/shop/product/edit` 返回了提示语而不是商品ID）。

## 为什么需要它们

本机 `mvn` 拉不到私服依赖（`cn.cikian:cik-oss` / `log3` / `crydis`），无法构建整个工程、
也就无法用常规方式启动后端做联调。这些脚本用 Node 直接构造 JeecgBoot 请求
（MD5 签名 + JWT），绕过前端，直接观察接口的真实响应。

## 准备 token

JeecgBoot 的登录态同时存在于 JWT 与 Redis（`prefix_user_token:*`）。
两种取法任选其一：

**A. 直接取浏览器当前登录态的 token（推荐，最省事）**

```bash
# 从 Redis 里找到当前有效 token（键名就是 JWT 本身）
redis-cli --scan --pattern 'prefix_user_token:*'
```

```powershell
$env:CK_TOKEN = '<上一步得到的 JWT>'
```

**B. 自签 JWT**（签名密钥是 `sys_user.password` 字段的值，**不是明文密码**）

```powershell
# 从数据库读出 password 字段
#   SELECT password FROM sys_user WHERE username='admin';
$env:CK_PWD_FIELD = '<password 字段的值>'
```

> 注意：自签 token 的签名虽然正确，但服务端通常会校验 Redis 中的登录态，
> 因此大概率会被判为「Token失效，请重新登录!」，此时请改用方式 A。

## 用法

```powershell
# 免登录探活 + 判断错误 id 场景（不需要 token）
node .probe/product-flow.probe.mjs

# 管理端接口（需要 token）
$env:CK_TOKEN = '<JWT>'
node .probe/admin-api.probe.mjs      # 列表 / 详情 / skuList / 品牌 / 标签
node .probe/save-flow.probe.mjs      # 完整复现「新增 → 保存并继续编辑」→ 并清理测试数据
```

## 各脚本做什么

| 脚本 | 用途 |
|------|------|
| `product-flow.probe.mjs` | 店铺端免登录接口探活；用一个**不存在的 id** 复现错误请求，确认后端会返回什么 |
| `admin-api.probe.mjs` | 带 token 打管理端列表/详情/skuList/品牌/标签，并**故意用提示语当 id** 复现故障请求 |
| `save-flow.probe.mjs` | 完整跑一遍新增 → 回填详情 → 编辑 → 再次详情，**验证 add/edit 的 result 是否都为商品ID**，最后软删除测试商品 |
| `dict-upstream.probe.mjs` | 验证新增字典 `shop_upstream_source`（淘宝/京东/1688/闲鱼）能否被管理端读到，并检查运行中的后端是否已带上 `upstreamSource`/`upstreamUrl` 字段 |

### 踩坑：签名 header 的名字

签名相关的 header 必须是 **`X-TIMESTAMP`** 与 **`X-Sign`**（见后端 `CommonConstant`），
**不是** `timestamp` / `sign`。写成后者时后端 `getHeader("X-TIMESTAMP")` 取不到值，
会报「Sign签名校验失败，请求参数不完整！」——这条报错文案有误导性，
实际原因是**时间戳 header 名不对**，而不是参数缺失。

另外 `/sys/dict/getDictItems/**` 走签名拦截器，而部分业务接口不校验签名，
所以同一个脚本里不同接口的表现可能不一致，排查时不要想当然。

## 前端 / DevTools 诊断探针

用于排查「打开 F12 后页面刷不动、DevTools 面板卡死」这类**浏览器侧**问题（结论见下方说明）。
它们不改任何业务代码，只读取状态与统计。

```powershell
# 1) 取当前登录 token（只读 SCAN/GET/TTL，不写 Redis）
node .probe/redis-token.probe.mjs

# 2) 先用调试端口起一个 Edge（独立临时 profile，不影响你日常浏览器）
#    msedge.exe --headless=new --remote-debugging-port=9334 --auto-open-devtools-for-tabs \
#      --user-data-dir=%TEMP%\ck-dsh-edge http://127.0.0.1:3100/

# 3) 注入登录态，打开重型路由，统计给 DevTools 的压力
$env:CDP_PORT='9334'; $env:CK_TOKEN='<jwt>'
$env:PATHS='/dashboard/analysis,/shop/product'
node .probe/heavy-page.probe.mjs

# 4) 连续刷新，验证「只能刷一次」是否复现
$env:CDP_PORT='9334'; $env:ROUNDS='6'; node .probe/reload-repeat.probe.mjs

# 5) 读取本机 Edge 的 DevTools 设置 / 扩展清单（只读 profile 文件）
node .probe/edge-devtools-settings.probe.mjs
node .probe/edge-extensions.probe.mjs
```

结论：admin-client 与 dev server 侧健康（登录态下 `/dashboard/analysis`、`/shop/product` 各
555–679 个请求、567–670 个脚本、DOM 1900–2400 节点，连刷 6 次含 Disable cache 均 2.4–2.9s 完成，
主线程与 DevTools 前端 RTT 均为毫秒级，0 次 debugger 暂停、0 异常）。问题出在浏览器 DevTools 会话。

## 安全说明

- 脚本不硬编码任何密钥，token 与 password 字段一律从环境变量读取。
- `save-flow.probe.mjs` 会在最后软删除自己创建的商品；若中途失败，可能残留 `SPU-PROBE-*` 记录，
  可用以下 SQL 清理：
  ```sql
  -- 先取出 id，再逐表清理（子表无外键级联）
  SELECT id FROM ck_shop_product WHERE spu_code LIKE 'SPU-PROBE-%';
  ```
