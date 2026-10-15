# 优化任务交接说明（ck-shop）

> 本文是一次"整体检查 → 清单确认 → 分批执行"的收尾交接：**做了什么、证据在哪、还剩什么、需要谁决策**。
> 配套阅读：[后端构建与验证手册](./backend-runbook.md)（改动后必须走的流程与三个静默陷阱）。

## 一、范围与前提（用户已确认）

- 全项目检查后执行清单，**除 P1-3 之外全部执行**：P1-3（lint / typecheck / test 质量门禁脚本接线）因本地 `mvn` 拉不到私有依赖而跳过。
- 执行顺序：**先分批提交基线，再优化**。
- 不越界：涉及"改数据库状态 / 改生产可用性 / 轮换密钥"的动作一律不擅自执行，只在本文列出待决策项。

## 二、已完成（按清单块）

### P0 安全与正确性

| 项 | 落点 |
|---|---|
| 跨店越权与配置类隐患 | `StoreResolver`（默认店铺回退关闭）、`PriceTokenUtils`（启动校验密钥）、组织归属校验 |
| 多店铺可见性规则**收敛为唯一实现** | `common/ShopOrgUtils.matchesOrg`（原先散落 5 处内联判断，其中 2 处写成 `!x.startsWith(storeOrgCode)`，空串时恒为 true = **校验被绕过**） |
| LIKE 转义**收敛为唯一实现** | `common/ShopSqlUtils.escapeLike`（原先 3 处各写一份） |
| 管理端授权 | `ShopAdminOrderStatsController.requireGlobalTaskPermission()`；退款管理 `requireAdminOrder` / `adminRefundableAmount` |
| BFF 侧防护 | 登录限流、CSRF 同源校验、轮播 URL 白名单、分页/批量上限、超时与错误码映射 |

### P1 工程化与仓库卫生（不含 P1-3）

- 仓库卫生：`.gitignore` / `.gitattributes` 重写、README 重写、`docs/README.md` 索引、误入库文件清理、设计稿移出仓库（`design-references/`）。
- 配置加固：9 个 profile 关闭非 dev 的 Druid 监控台、签名密钥改环境变量、`.env.example` 补齐；`docker-compose*.yml` 凭据外置并标注"本仓库不可直接运行"。
- 数据库基线：`db/ck-shop-schema-baseline.sql`（34 表建表语句）+ 生成器 `db/tools/DumpShopSchema.java`。
- **文档化**：`docs/backend-runbook.md`（构建/验证流程 + 三个曾静默误导的坑）。

### P2 一致性清理

- i18n 三语各裁剪 146 个未使用 key（659 → 513）。
- 后端纯函数/只读逻辑外移为可复用组件：`ShopPaymentSupport`、`ShopRefundSupport`、`ShopAccountSupport`、`ShopPromotionSupport`；`ShopPaymentServiceImpl` 902→828、`ShopRefundServiceImpl` 1113→1078、`ShopAccountServiceImpl` 1048→1004、`ShopPromotionServiceImpl` 1245→1199、`ShopProductServiceImpl` 1558→1549。
- 共享口径：`utils/orderRefund.ts`（退款原因映射 + 金额/可撤销判断）。

### P1-4 巨型文件拆分

| 文件 | 起始 | 现在 | 方式 |
|---|---|---|---|
| `nuxt-shop/app/pages/profile.vue` | ~1950 | **444** | 5 个面板组件 + 5 个 composable + 2 个类型文件 |
| `nuxt-shop/app/pages/products/[slug].vue` | 1059 | **525** | `useProductDetail` + Gallery / PurchaseBar / DetailSections |
| `nuxt-shop/app/pages/orders/[orderNo].vue` | 824 | **261** | `useOrderDetail` + `order/DetailCards` |
| `nuxt-shop/app/pages/cart.vue` | 718 | **451** | `useCartPage` |

后端 God 类只做了"只读取值/纯函数外移"（收益 3–8%）；**按职责切真 bean 需要授权**（见第四节）。

## 三、验证资产与当前证据

`.probe/`（gitignored 本地资产）里的 `final-verify.ps1` 一次跑完 8 步：

| 步骤 | 断言数 | 覆盖 |
|---|---|---|
| [3a] P0-BFF | 9 | 会话、验证码、登录限流、CSRF、轮播白名单 |
| [3b] 匿名页面冒烟 | 10 | 首页/商品/分类/登录/注册/忘记密码/多语言 |
| [3c] i18n 渲染 | 8 | 无裸 key |
| [3d] 已登录页面 | 10 | profile 五个 tab、orders、cart、address、checkout |
| [3e] 商品详情页 | 11 | 图集/购买栏/区块三个组件级断言 |
| [3f] 客户端渲染页面 | 11 | `/zh/cart`、订单详情（这两个页面挂载后才取数，SSR 断言会误判） |
| [3g] 多店铺隔离 | 9 | 两店零交集、跨店详情不返回、未知店码 0 商品、**无组织码店铺 0 商品** |
| [3h] 管理端授权 | 10 | 匿名 401 / 会员 510 / 管理员 200（只读端点） |

**最近一次全量结果：8 步全绿、共 78 条断言、0 失败**，跑在 `clean + -am` 打包出的当前源码产物 + 新构建前端之上。

运行方式见 `docs/backend-runbook.md`；需要 `CK_SHOP_TOKEN` / `CK_ADMIN_TOKEN` / `CK_ORDER_NO`（测试账号与订单见下方备注）。

## 四、待决策项（我未擅自执行）

| # | 事项 | 证据 / 建议 |
|---|---|---|
| 1 | **C2 支付回调原子性** | `handleNotify` 的 `catch` 吞异常 → 部分结算照常提交；去重行仍占唯一键 → 渠道重推被当重复并回成功 ack → 半完成状态再无自愈机会。另 `mockConfirm` 自调用导致内层 `@Transactional` 失效。建议：结算跨 bean 包 `REQUIRES_NEW` + 失败不回成功 ack。**复现需要一笔测试订单（写数据），等授权** |
| 2 | **Flyway 迁移完全没在执行** | 启动即 `Detected applied migration not resolved locally: 3.9.5.2` + `checksum mismatch 3.9.5.12` → 任何新迁移都不会落库。需 `flyway repair` 或人工核对 `flyway_schema_history` |
| 3 | **后端真 bean 拆分** | `ShopOrderServiceImpl` 1670 / `ShopProductServiceImpl` 1549；按"查询/校验/落库/通知"切分可降到 500–800 行，但不改事务与调用顺序，需授权 |
| 4 | **`/shop/store/*`、`/shop/tag/*` 读接口权限码** | 实测：匿名 401；**任意店铺会员**可读 `store/list|all`、`tag/list|all`（200 + 数据），返回体含 `departOrgCode`（多店铺隔离所依赖的内部组织编码）、`domain`、`createBy`。三个口径：加权限码 / 拆店铺端公开投影 / 判定可公开 |
| 5 | 其他 | 演示 AES/签名密钥轮换（AES key 是 Java 常量，需前后端协同 + 重启）；商品搜索 `LIKE '%kw%'` 全表扫描（需跨 MySQL/PG/Oracle/SQLServer/达梦 策略）；`normalizeLanguage` 五处实现语义不一致（商品不 trim/不改大小写，促销 trim+小写）；`refundTypeLabel` 在 `ShopRefundSupport.toVO` 写死中文，与"标签一律用后端字段"约定冲突 |

## 五、过程中发现并修掉的自伤（避免后人重踩）

1. **后端长期"改了但没跑"**：一直用 `mvn compile` 当证据，连续掩盖三个问题 —— 残留 demo 测试源导致**根本打不出包**；打包少 `-am` 会从本地仓库取**陈旧 `ck-shop` 制品**（表现为接口 404、会话误判未登录）；少 `clean` 会把已删源码的编译残留打进 jar（表现为启动失败 `ClassNotFoundException: ...JeecgDemo`）。三者都写进了 `docs/backend-runbook.md`。
2. **两个 `/profile` 500**：基线里 `watch(isLoggedIn, …, {immediate:true})` 早于 `const profileLoading = ref()`（TDZ → SSR 500）；以及根 `composables/` 不自动导入导致的 `formatDate is not defined`。
3. **验证假象两例**：`nuxt dev` 只监听 localhost，用 `127.0.0.1` 会得到 426 导致整轮误报；`/zh/cart`、订单详情挂载后才取数，SSR 断言会把它们误判为"已渲染"（后来补了真实浏览器渲染的 [3f]）。
4. **两次自我更正**：曾用 JSON 里任意 `"id"` 比较两店商品交集得出"交集 6 个"，改用 `spuCode` 后实际是 **0**；曾因**自己粘贴 token 弄坏一个字符**导致 5 个管理端探测全 401，换回正确 token 后全 200 —— 这两条都不构成证据。

## 六、运行时状态

- 后端 9951：跑的是 `clean + -am` 打包出的**当前源码产物**（`/api/auth/session` = authenticated、`/api/order/detail` 200）。
- 前台 3001：跑的是通过 8 步验证的构建；旧产物备份在 `target/jeecg-system-start-3.9.5.jar.bak-r21`。
- 测试账号/数据：店铺会员 `u_3havsluh`（购物车为空、无订单）；`admin` 持有全部订单（均 CANCELLED）；测试订单 `SO202610141917375928R71W6B1OG`；测试商品 `/zh/products/2109574599579942914`。
- **凭据不要写进本文**：token 从 Redis `prefix_user_token:*` 取，且不要出现在任何用户可见输出里。
