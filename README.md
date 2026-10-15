# ck-shop · 多店铺电商系统

`ck-shop` 是基于 **JeecgBoot（Java / Spring Boot）** 与 **Nuxt** 构建的**多店铺（多租户前台）电商系统**。
一个管理后台 + 一个后端，支撑多个独立前台店铺：每个前台站点用**服务端配置的店铺编码**（`STORE_CODE`）标识自己的身份，
商品、订单、购物车、促销等数据按店铺隔离，"一个管理端、多个前台"。

> 本仓库派生自脚手架项目 `ck-boot-starter`（其底座即 JeecgBoot 官方单仓结构）。
> 与底座的同步流程见文末 [底座升级 SOP](#九底座升级-sop保留自脚手架模板)。

## 一、仓库组成

| 子项目 | 角色 | 技术栈 | 开发端口 |
|--------|------|--------|----------|
| [`ck-server/`](./ck-server) | 后端服务（含商城业务模块 `ck-shop`） | Java 17 / Spring Boot / JeecgBoot 3.9.5 / MyBatis-Plus / MySQL + Redis | **9951**（dev profile） |
| [`nuxt-shop/`](./nuxt-shop) | 店铺前台（多店铺，SSR + BFF） | Nuxt 4 / Vue 3 / Tailwind 4 / Nitro | **3000**（dev）、**3001**（生产，供隧道回源） |
| [`admin-client/`](./admin-client) | 管理后台（商品/订单/店铺/促销等运营配置） | Vue 3 / Vite / Ant Design Vue 4 / Pinia（JeecgBoot Vue3） | **3100** |

请求链路：`浏览器 → nuxt-shop（Nitro BFF，同源 /api/*）→ ck-server（9951 /api）`。
前台**不直连** Java 后端：签名密钥、AES 密钥、登录 token 都只存在于 Nuxt 服务端（token 存 httpOnly cookie）。

## 二、目录结构

```
ck-shop/
├── ck-server/                    # 后端（Maven 多模块）
│   ├── db/                       # 建库脚本与增量升级脚本（*.sql）
│   ├── jeecg-boot-base-core/     # 底座公共能力（工具、鉴权、OSS、日志等）
│   ├── jeecg-boot-module/
│   │   ├── ck-shop/              # ★ 商城业务模块（org.jeecg.modules.shop.*）
│   │   ├── ck-system/            # 本项目对 system 模块的定制
│   │   └── jeecg-boot-module-airag/
│   ├── jeecg-module-system/
│   │   ├── jeecg-system-api/     # 系统模块 API（Entity/VO/常量）
│   │   ├── jeecg-system-biz/     # 系统模块业务实现
│   │   └── jeecg-system-start/   # ★ 启动模块：org.jeecg.JeecgSystemApplication
│   └── pom.xml
├── nuxt-shop/                    # 店铺前台
│   ├── app/                      # 页面/组件/中间件/插件（Nuxt 4 的 app/ 结构）
│   ├── composables/              # 前端状态与数据复用（useAuth/useCart/useFavorites…）
│   ├── server/                   # Nitro BFF：api/ + middleware/ + utils/
│   ├── types/                    # 前后端共享类型（含语言清单唯一来源 store-locale.ts）
│   ├── i18n/locales/             # en / zh / fr 语言包
│   └── docs/                     # 店铺端文档
├── admin-client/                 # 管理后台（JeecgBoot Vue3）
│   ├── src/api/ src/views/ src/components/ src/store/ …
│   └── build/                    # Vite 插件与构建脚本
├── docs/                         # 接口契约 / 交付说明 / 验证记录（见 docs/README.md）
├── design-references/            # 设计稿原图（本地保留，已 gitignore，不入库）
├── saved-images/                 # AI 生图输出（本地保留，已 gitignore，不入库）
└── docker-compose.yml            # ⚠️ 上游遗留：构建上下文指向 ./jeecg-boot/ 等不存在的目录，当前不可用（见文件头）
```

## 三、环境要求

- **JDK 17**（`ck-server/pom.xml` 的 `java.version=17`）、**Maven 3.8+**
- **MySQL 5.7 / 8.0**、**Redis 5+**（请自行安装或用既有实例。根目录 `docker-compose.yml` 与 `start-docker-compose.*`
  是 JeecgBoot 上游遗留：构建上下文指向本仓库并不存在的 `./jeecg-boot/`、`./jeecgboot-vue3/`，**当前无法构建**，勿直接使用）
- **Node.js 20 LTS 及以上**（`admin-client` 的 `engines` 为 `^18 || >=20`；Nuxt 4 建议 20+）
- **npm** 用于 `nuxt-shop`（仓库内只有 `package-lock.json`），**pnpm** 用于 `admin-client`（仓库内只有 `pnpm-lock.yaml`）

## 四、环境变量（.env）配置

> **只提交 `*.env.example`，真实密钥一律留在被 gitignore 的 `.env` 里，永不入库。**
> 仓库已忽略 `ck-server/.env`、`ck-server/**/.env`、`nuxt-shop/.env`、`nuxt-shop/.env.*`（`!.env.example`）。

### 4.1 后端 `ck-server/.env`

```bash
cp ck-server/.env.example ck-server/.env
```

按 `ck-server/.env.example` 填写：`MYSQL_*`、`REDIS_*`、`DRUID_STAT_*`、`JEECG_SIGNATURE_SECRET`、
以及可选的邮件 / OSS / MinIO / AI / OAuth 密钥。**`JEECG_SIGNATURE_SECRET` 必须与 `nuxt-shop` 的 `SIGN_SECRET` 一致**，否则前台所有接口签名校验失败。

### 4.2 店铺端 `nuxt-shop/.env`

```bash
cp nuxt-shop/.env.example nuxt-shop/.env
```

关键项（完整说明见 `nuxt-shop/.env.example` 注释）：

| 变量 | 说明 |
|------|------|
| `API_BASE_URL` | 后端地址，默认 `http://127.0.0.1:9951/api` |
| `STORE_CODE` | **本站点对应的店铺编码**（后台【店铺管理】里维护）；BFF 通过 `X-Store-Code` 头透传，前端不可见不可篡改 |
| `AES_KEY` / `AES_IV` | 密码 AES 加密，必须与后端一致 |
| `SIGN_SECRET` | 接口签名密钥，必须与后端 `JEECG_SIGNATURE_SECRET` 一致 |
| `STORE_SITE_URL` / `STORE_SITE_NAME` / `STORE_PUBLIC_ORIGIN` | 站点域名与站名（影响 canonical / sitemap / OG / OAuth 回调） |
| `COOKIE_SECURE` | 登录 cookie 是否带 `Secure`；仅 http 隧道或局域网 IP 调试时设 `false` |

安全兜底：若密钥仍是 JeecgBoot 开源默认值，前台会**拒绝启动**；只有本地开发才在被 gitignore 的 `.env` 里显式设置 `ALLOW_INSECURE_DEMO_SECRETS=true`。

### 4.3 管理端 `admin-client/.env*`

`admin-client/.env`（端口 3100、标题、SSO/qiankun 开关）与 `.env.development` / `.env.production` 等是 JeecgBoot 约定的**构建期**配置，已入库且不含密钥。
后端地址在 `.env.development` 的 `VITE_GLOB_API_URL` / `VITE_PROXY` 中调整。

## 五、启动与构建

### 5.1 后端 `ck-server`（9951）

```bash
# 1) 建库：执行 ck-server/db/ 下的脚本（升级流程见 ck-server/db/版本升级说明.md）
# 2) 启动：dev profile 端口为 9951
cd ck-server
mvn -pl jeecg-module-system/jeecg-system-start -am clean package -DskipTests
cd jeecg-module-system/jeecg-system-start
mvn spring-boot:run
# 也可在 IDE 中直接运行 org.jeecg.JeecgSystemApplication
```

### 5.2 店铺端 `nuxt-shop`（开发 3000 / 生产 3001）

```bash
cd nuxt-shop
npm install
npm run dev            # http://localhost:3000（nuxt dev --host）

npm run build          # 生产构建到 .output/
PORT=3001 npm start    # 生产启动（读 .env），隧道 localPort 指向 3001
```

> 开发环境用 3000；若通过 frp 隧道（如 `nuxt.frp.cikian.cn`）对外，把回源端口设为 **3001**，避免与本地开发端口互相干扰。

### 5.3 管理端 `admin-client`（3100）

```bash
cd admin-client
pnpm install
pnpm dev               # http://localhost:3100（后端默认代理到 localhost:8080/jeecg-boot）
pnpm build             # 生产构建到 dist/
```

## 六、商城后端模块布局（`ck-server/jeecg-boot-module/ck-shop`）

包根：`org.jeecg.modules.shop`，按领域分包，每个领域内部固定为 `controller / service / entity / mapper / vo`：

| 包 | 职责 |
|----|------|
| `store` | 店铺（多店铺主数据、域名与店铺编码） |
| `product` | 商品 SPU / 规格 / SKU、商品列表与详情 |
| `category` | 商品分类 |
| `cart` | 购物车（按店铺隔离、失效置灰、加购流水） |
| `order` | 订单（下单、预览、取消、确认收货、退款） |
| `promotion` | 促销活动与优惠券 |
| `favorite` | 收藏 |
| `account` | 店铺端账号体系（注册 / 登录 / 验证码 / Google OAuth / 找回密码） |
| `home` / `tag` / `options` / `common` | 首页装修、标签、字典选项、公共能力 |

## 七、文档

- [`docs/README.md`](./docs/README.md) —— **文档总索引**（接口契约、交付说明、验证记录、排查记录）
- [`nuxt-shop/docs/项目结构与技术栈说明.md`](./nuxt-shop/docs/项目结构与技术栈说明.md) —— 店铺端结构、BFF 链路与签名机制
- [`ck-server/db/版本升级说明.md`](./ck-server/db/版本升级说明.md) —— 数据库脚本与版本升级
- `admin-client/README.md`、`admin-client/CLAUDE.md` —— 管理端开发约定

## 八、仓库卫生约定

- **不入库的大体积/临时内容**：`design-references/`（26 张设计稿原图）、`saved-images/`（AI 生图输出）、
  `.probe/` 与 `nuxt-shop/.probe/`（本地排查探针）、`*.class` / `*.orig` / `*.bak`、各子项目构建产物。
  设计稿看板见 [`docs/store-redesign/index.html`](./docs/store-redesign/index.html)（图片走 `../design-references/`）。
- **只提交 `*.env.example`**，真实 `.env` 永不入库。
- 这些体积内容此前已进入 git 历史；**历史瘦身需要单独的 `git filter-repo` 决策**，不在日常提交范围内。

---

## 九、底座升级 SOP（保留自脚手架模板）

`ck-shop` 由脚手架 `ck-boot-starter` 派生而来，底座即 JeecgBoot 官方单仓结构。
下面两套流程用于持续吸收上游与底座的更新。

### 场景 A：脚手架（`ck-boot-starter`）吸收 JeecgBoot 官方更新

当 JeecgBoot 官方发布新版本或安全补丁时，由脚手架维护者在 `ck-boot-starter` 项目中执行以下同步：

```bash
cd ck-boot-starter

# 1. 切到官方镜像分支并拉取最新官方代码
git checkout official-upstream
git pull upstream main

# 2. 切回脚手架开发主分支
git checkout main

# 3. 合并官方分支更新
git merge official-upstream -m "merge: sync latest official JeecgBoot updates"

# 4. 在 IDE 中解决冲突并提交推送
git push origin main
```

### 场景 B：业务项目（如 `ck-shop`）吸收脚手架更新

当脚手架 `ck-boot-starter` 更新了底层组件或同步了官方特性后，各业务项目可快速完成底层升级：

```bash
cd ck-shop

# 1. 拉取脚手架底座仓库的最新变更
git fetch base-template

# 2. 将脚手架最新的 main 分支合并到业务项目中
git merge base-template/main -m "merge: sync core architecture updates from ck-boot-starter"

# 3. 在 IDE 中完成代码冲突解决后，提交并推送到业务私有库
git push origin main
```

### 仓库源（Git Remote）配置参考

项目创建完成后，可在根目录下运行 `git remote -v` 检查配置是否正确：

- **`ck-boot-starter` 脚手架项目：**
  - `origin` → `ck-boot-starter.git`（读写）
  - `upstream` → <https://github.com/jeecgboot/JeecgBoot.git>（只读）
- **`ck-shop` 等派生业务项目：**
  - `origin` → `ck-shop.git`（读写）
  - `base-template` → `ck-boot-starter.git`（只读）

### 基于本脚手架派生新业务项目的流程（备查）

**请勿直接复制/粘贴本地文件夹**，否则会丢失后续同步上游的版本基因：

```bash
# 1. 克隆脚手架到新的业务项目目录
git clone <你的 ck-boot-starter 私有库地址.git> ck-shop
cd ck-shop

# 2. 将原来的脚手架远程源标记为 base-template
git remote rename origin base-template

# 3. 绑定业务项目自己的私有仓库为 origin
git remote add origin <你的 ck-shop 私有库地址.git>

# 4. 推送初始基线
git push -u origin main

# 5. 之后日常提交直接作用于业务仓库 origin
git add .
git commit -m "feat: init shop module"
git push origin main
```
