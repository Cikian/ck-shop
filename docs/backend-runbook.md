# 后端构建与验证手册（ck-server）

> 这份手册来自一次真实的排障：后端长期"改了但没跑"，只用 `mvn compile` 当证据，结果连续掩盖了三个问题
> （打不出包、打包嵌入了陈旧制品、打包带上了陈旧编译产物）。下面每一步都是踩过的坑。

## 1. 标准流程（每次改完后端都要走完）

```powershell
# 0) 记下当前产物，便于回滚
Copy-Item ck-server\jeecg-module-system\jeecg-system-start\target\jeecg-system-start-3.9.5.jar `
          ck-server\jeecg-module-system\jeecg-system-start\target\jeecg-system-start-3.9.5.jar.bak

# 1) 先停后端：Windows 会锁住正在运行的 jar，否则 repackage 必失败
#    （症状：spring-boot-maven-plugin:repackage ... Execution repackage failed）
$p = (Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq 9951).OwningProcess
Stop-Process -Id $p -Force

# 2) 环境变量：DB/Redis/密钥都在 ck-server/.env（gitignored），必须先注入
Get-Content ck-server\.env | Where-Object { $_ -match '=' -and $_ -notmatch '^\s*#' } | ForEach-Object {
  $k,$v = $_ -split '=',2; Set-Item "env:$($k.Trim())" $v.Trim()
}
$env:JAVA_HOME = 'D:\Develop\jdk\jdk-17.0.14'   # PATH 里的 java 是 1.8，必须显式指定 JDK17

# 3) 打包：clean 与 -am 都不能省（原因见第 3 节）
mvn -o -pl jeecg-module-system/jeecg-system-start -am -DskipTests clean package

# 4) 校验产物内容（不是"编译通过"就够了）
#    内嵌 ck-shop-3.9.5.jar 必须：含本次改动的类、不含已删除的类/XML
(见第 4 节脚本)

# 5) 启动 → 冒烟 → 跑回归
java -jar ...\jeecg-system-start-3.9.5.jar       # 9951，约 40 秒
#   冒烟：GET /api/auth/session（应 authenticated=true）、GET /api/order/detail（200）
#   回归：.probe/final-verify.ps1（六套 + [3g] 多店铺隔离）
```

## 2. 教训一：`-DskipTests` 不跳过测试**编译**

移除演示代码时删掉了 `org.jeecg.modules.shop.test.*` 的生产类，但 `jeecg-system-start/src/test` 下
还有 `SampleTest` / `MockControllerTest` 引用它们。`mvn compile` 只编译主源码 → 一路绿灯；
`mvn package` 会编译测试源码 → **BUILD FAILURE**。也就是说那段时间后端**根本打不出包**。

- 想跳过测试编译要用 `-Dmaven.test.skip=true`；
- 但更正确的是**修好源码**（本仓库已删除那两个只针对已删 demo 的测试源）。

## 3. 教训二：`-am` 与 `clean` 各自对应一类"静默错误"

| 省略 | 会发生什么 | 现象 |
|---|---|---|
| `-am` | Maven 从本地仓库取 `org.jeecgframework.boot3:ck-shop` 的**陈旧制品**塞进 fat jar | 接口 404（例如 `/api/shop/account/profile`），会话全判未登录 —— 看起来像"代码回归"，其实是构建 |
| `clean` | `target/classes` 里**已删除源码的残留**（例如 demo 的 `*Mapper.xml`）被打进 jar | 启动失败：`Could not resolve type alias '...JeecgDemo'` → `sqlSessionFactory` 起不来 |

判定方法（不用改代码就能分清"产物问题"还是"源码问题"）：

```powershell
# 解开 fat jar 里的内嵌 ck-shop.jar，比对条目数与关键类
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[System.IO.Compression.ZipFile]::OpenRead($fatJarPath)
$e=$zip.Entries | Where-Object FullName -eq 'BOOT-INF/lib/ck-shop-3.9.5.jar'
[System.IO.Compression.ZipFileExtensions]::ExtractToFile($e,"$env:TEMP\ck.jar",$true)
$inner=[System.IO.Compression.ZipFile]::OpenRead("$env:TEMP\ck.jar")
$inner.Entries | Where-Object { $_.FullName -match 'shop/(account|test)/' } | Select-Object -Expand FullName
```

## 4. 前端/联调侧的坑（同样踩过）

- **`nuxt dev` 只监听 `localhost`**：用 `127.0.0.1` 访问会得到 **426 Upgrade Required**，
  曾导致一整轮"已登录页面校验"全部误报失败。脚本已加 `-HostName` 参数。
- **部分页面是 `onMounted` 才取数**（`/zh/cart`、`/zh/orders/<no>`）：SSR 只有外壳，
  抓 SSR HTML 的断言会把它们误判为"已渲染"。必须用真实浏览器渲染后再校验
  （`.probe/cdp-dump.mjs` + `smoke-client-pages.ps1`）。
- **后端不可用时**，这些页面会全部退化成登录页 —— 排查时先看 9951 在不在。

## 5. 待决定的正确性问题（未改，等决策）

### 5.1 支付回调（C2）：失败会被"静默吞掉"

`ShopPaymentServiceImpl.handleNotify`（`@Transactional`）的结构：

```
saveNotifyLog(... "RECEIVED")     // 去重行，唯一键 dedupKey
if (notifyLog == null) return ok("重复回调，已忽略")   // 渠道重推被丢弃，且回 SUCCESS ack
try { handlePayNotify(...) } catch (Exception e) { updateNotifyLog(FAILED); /* 不 rethrow */ }
```

后果：异常被吞 → 部分结算**照常提交**；去重行仍占着唯一键 → 渠道重推被当成重复并回成功应答 →
**半完成状态再无自愈机会**。另外 `mockConfirm` 与 `handleNotify` 同为 `@Transactional` 且是
**自调用**（不走代理），内层事务注解实际不生效，所以"失败整体回滚"目前也不成立。

修法（建议先做前两步，风险最小）：① 结算跨 bean 包进 `REQUIRES_NEW`；② 失败时不回 SUCCESS ack；
③ 去重行唯一性只对"已成功处理"生效（或失败时删除该行）。

复现入口：`POST /shop/order/store/pay/mock-confirm`（内部走的是与生产完全相同的
`handleNotify` 链路）—— 需要一笔属于测试会员的 `WAIT_PAY` 订单 + `MOCK` 支付单。

### 5.2 其他

- Flyway 校验失败（`Detected applied migration not resolved locally: 3.9.5.2` +
  `checksum mismatch 3.9.5.12`）→ 迁移**完全没在执行**，需 `repair` 或人工核对 `flyway_schema_history`。
- 未受保护的读接口缺权限码；
- 演示用 AES/签名密钥需轮换（AES key 是 Java 代码常量，需前后端协同 + 重启）；
- 商品模糊搜索 `LIKE '%kw%'` 全表扫描，需跨库（MySQL/PG/Oracle/SQLServer/达梦）策略；
- `normalizeLanguage` 在 5 个文件里各一份且语义不一致（商品不 trim/不改大小写，促销 trim + 转小写）；
- `refundTypeLabel` 在 `ShopRefundSupport.toVO` 里写死中文，与"标签一律用后端字段"的约定冲突。
