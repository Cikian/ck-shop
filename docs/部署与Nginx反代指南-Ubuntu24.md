# ck-shop 生产部署与 Nginx 反代指南（Ubuntu 24.04）

> 适用仓库：`ck-shop`（`ck-server` + `nuxt-shop` + `admin-client`）
> 目标环境：单台 Ubuntu 24.04 LTS，宿主机直接跑 JDK / Node / MySQL / Redis / MongoDB，Nginx 做入口与反代。
> 文中域名、口令、路径都是占位符，**上线前逐项替换**。
> 相关既有文档：[根 README](../README.md)、[后端构建与验证手册](./backend-runbook.md)、[数据库脚本说明](../ck-server/db/版本升级说明.md)。
> 现成可拷的配置文件（systemd / Nginx / 生产覆盖配置 / 两个前端的 .env 模板）在 [`deploy/`](../deploy/README.md)，那里的 [执行清单](../deploy/README.md) 是按「后端 → 管理端 → 店铺端 → Nginx」排好的最小步骤；本文负责解释每条为什么这么做。

---

## 0. 一张图看懂部署形态

```
                    ┌──────────────────────────── Nginx :80/:443 ────────────────────────────┐
 浏览器 ──https──▶  │ shop.example.com   → 127.0.0.1:3001 (Nuxt SSR + Nitro BFF)             │
                    │ admin.example.com  → 静态 dist + /api/ → 127.0.0.1:9951                  │
                    └─────────────────────────────────────────────────────────────────────────┘
                                                   │
         nuxt-shop(.output, Node)  ── /api/* 及内部调用 ──▶ ck-server(Spring Boot, /api)
         admin-client(纯静态 dist) ── /api/* ───────────▶ ck-server(Spring Boot, /api)
                                                   │
                             MySQL 8 (:3306) + Redis 7 (:6379) + MongoDB (:27017，首页轮播等文档数据)
```

关键点（决定了后面所有配置）：

| 事实 | 位置 | 影响 |
|---|---|---|
| 后端 **context-path = `/api`** | `application-dev.yml:14`、`application-prod.yml:6` | Nginx 只需 `location /api/ → 127.0.0.1:9951`，**不做 rewrite** |
| 后端 dev 端口 **9951**，prod 默认 **8080** | `application-dev.yml:10`、`application-prod.yml:2` | 建议把 prod 改成 9951，与 `nuxt-shop/.env` 的 `API_BASE_URL` 保持一致 |
| 店铺前台**不直连**后端 | `README.md:18`、`nuxt-shop/server/utils/apiClient.ts` | 浏览器只访问同源 `/api/*`，由 Nitro BFF 转发；密钥只在服务端 |
| 管理端**同源** `/api` 调后端 | `admin-client/.env.production:16` | `.env.production` 现在的 `/jeecgboot` 是上游默认值，**必须改** |
| 管理端签名密钥**硬编码在源码里** | `admin-client/src/utils/encryption/signMd5Utils.js:3` | 必须与后端 `JEECG_SIGNATURE_SECRET` 一致，否则要改源码重新打包 |
| 店铺端登录态是 httpOnly cookie | `nuxt-shop/server/utils/authCookie.ts:18,52-58` | 必须 HTTPS（`COOKIE_SECURE=true`），且 Nginx 要透传 `Host` / `X-Forwarded-Proto` |
| 店铺端 CSRF 校验按请求 origin 判同源 | `nuxt-shop/server/middleware/csrf.ts:26-34` | 少配一个转发头 → 所有写操作 403 |
| prod profile **配置不完整**（缺 Mongo、缺 .env 占位符、口令写死） | `application-prod.yml` vs `application-dev.yml` | 见 §5.2，必须用覆盖配置补齐 |

---

## 1. 域名与端口规划

| 用途 | 域名 | 端口 | 进程 |
|---|---|---|---|
| 店铺前台 | `shop.example.com` | 3001（仅 127.0.0.1） | `node .output/server/index.mjs` |
| 管理后台 | `admin.example.com` | 静态文件 | Nginx 直接托管 |
| 后端 API | 不单独暴露域名（复用 admin/shop 的 `/api`） | 9951（仅 127.0.0.1） | `java -jar` |
| MySQL | 127.0.0.1 | 3306 | `mysql` |
| Redis | 127.0.0.1 | 6379 | `redis-server` |
| MongoDB | 127.0.0.1 | 27017 | `mongod` |
| Nginx | 0.0.0.0 | 80 / 443 | `nginx` |

> 也支持 `shop.example.com` + `admin.example.com` + 单独 `api.example.com` 三域；但**不要**把管理端和后端放在不同域名下（会引入 CORS、还要额外放行 `X-Access-Token/X-Sign/X-TIMESTAMP/X-Version/X-Tenant-Id`，得不偿失）。

目录规划：

```
/opt/ck-shop/
├── ck-server/            # jar + .env + config/application-prod.yml
├── nuxt-shop/            # 源码 + .output + .env
├── admin-client/         # 源码 + dist（Nginx root）
├── upload/  webapp/      # 后端本地存储（uploadType=local 时才用）
├── logs/                 # 服务日志
└── backup/               # 数据库与产物备份
```

---

## 2. 系统准备与依赖安装

```bash
# 系统更新 + 基础工具
sudo apt update && sudo apt -y upgrade
sudo apt -y install curl gnupg ca-certificates git unzip vim ufw

# 创建运行用户（不要用 root 跑应用）
sudo useradd -r -m -s /bin/bash ckshop
sudo mkdir -p /opt/ck-shop/{logs,backup,upload,webapp}
sudo chown -R ckshop:ckshop /opt/ck-shop
```

### 2.1 JDK 17 + Maven

```bash
sudo apt -y install openjdk-17-jdk-headless maven
java -version     # 必须是 17.x（Spring Boot 4.1 / pom 的 java.version=17）
mvn -v
```

### 2.2 Node.js 22 + pnpm 9

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt -y install nodejs
node -v           # 必须 ≥ 22.12（nuxt 4.4.8 的 engines 是 ^22.12.0 || ^24.11.0；start 脚本还用了 --env-file-if-exists）
sudo corepack enable
sudo corepack prepare pnpm@9.15.0 --activate
pnpm -v
```

> `admin-client/pnpm-lock.yaml` 是 `lockfileVersion: '9.0'`，用 pnpm 9。
> 若 `@cikian/captcha-web` 等私有包在公网 registry 拉不到，需要在服务器上配置内网私服，或把依赖离线拷贝过去（见 §11 故障排查）。
> `corepack` 在新版 Node 是 opt-in，如提示找不到 `pnpm`，改用 `sudo npm i -g pnpm@9`。

### 2.3 MySQL 8

```bash
sudo apt -y install mysql-server
sudo mysql_secure_installation
```

`/etc/mysql/mysql.conf.d/zz-ck-shop.cnf`：

```ini
[mysqld]
character-set-server         = utf8mb4
collation-server             = utf8mb4_unicode_ci
max_allowed_packet           = 128M
explicit_defaults_for_timestamp = 1
default-time-zone            = '+08:00'
max_connections              = 500

[client]
default-character-set = utf8mb4
```

```bash
sudo systemctl restart mysql
```

> ⚠️ `lower_case_table_names` 只能在**初始化数据目录之前**改。本仓库 SQL 里的表名与实体 `@TableName` 都是小写，Ubuntu 默认值 `0`（区分大小写）可以正常跑；**不要**在已有数据后强行改它，MySQL 8 会拒绝启动。

### 2.4 Redis 7

```bash
sudo apt -y install redis-server
sudo sed -i 's/^# *requirepass .*/requirepass 换成强口令/' /etc/redis/redis.conf
sudo sed -i 's/^bind .*/bind 127.0.0.1 ::1/' /etc/redis/redis.conf
sudo systemctl restart redis-server
redis-cli -a '换成强口令' ping     # PONG
```

Redis 同时承担：登录态 token、字典缓存、分布式锁（`jeecg.redisson`）、验证码 ticket（`ck.crydis`）。**必须**设置密码且只监听本机。

### 2.5 MongoDB 8（首页轮播等文档数据，必需）

`ck-shop` 的首页装修走 `mongoplus`（`jeecg-boot-module/ck-shop/.../home/service/impl/CommonMongoServiceImpl.java`），而 `application-prod.yml` **没有** mongo 配置段，所以这一节和 §5.2 必须一起做。

```bash
curl -fsSL https://www.mongodb.org/static/pgp/server-8.0.asc \
  | sudo gpg -o /usr/share/keyrings/mongodb-server-8.0.gpg --dearmor
echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-8.0.gpg ] \
https://repo.mongodb.org/apt/ubuntu noble/mongodb-org/8.0 multiverse" \
  | sudo tee /etc/apt/sources.list.d/mongodb-org-8.0.list
sudo apt update && sudo apt -y install mongodb-org
```

建库与账号（库名、账号与 `ck-server/.env` 的 `MONGO_*` 保持一致）：

```bash
mongosh
> use ck-saas
> db.createUser({ user: "ck-saas", pwd: "换成强口令",
                  roles: [ { role: "readWrite", db: "ck-saas" } ] })
```

`/etc/mongod.conf`：

```yaml
net:
  bindIp: 127.0.0.1
  port: 27017
security:
  authorization: enabled
```

```bash
sudo systemctl restart mongod && sudo systemctl enable mongod
```

> 注意 `application-dev.yml:493` 的 `authenticationDatabase` 写死为 `ck-saas`，所以账号要建在 `ck-saas` 库里（不要建在 `admin`）。

### 2.6 Nginx

```bash
sudo apt -y install nginx
nginx -v          # Ubuntu 24.04 是 1.24.x → 用 listen 443 ssl http2;（不是 http2 on;）
```

### 2.7 可选依赖

- **pgvector / PostgreSQL**：只有用 AI RAG 知识库才需要；不用时 `PGVECTOR_*` 留空，后端仍能启动。
- **Upyun / 阿里云 OSS**：商品图片、后台附件上传走对象存储（`ck.oss.provider=upyun`、`ck-server/.env` 的 `CK_OSS_*`）。纯内网试用可改 `jeecg.uploadType=local`，文件落在 `jeecg.path.upload`。
- **SMTP**：店铺端邮箱验证码与定时任务发信必需（`MAIL_*`，注意 `MAIL_USERNAME` 必须是完整邮箱地址）。

### 2.8 内存与 swap

`nuxt build` 与 `mvn package` 都吃内存。2C4G 机器建议先加 4G swap：

```bash
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

## 3. 部署代码

任选一种（都只是把源码放到 `/opt/ck-shop`，真实密钥不在仓库里）：

```bash
# A. 服务器直接 clone（推荐，后续 git pull 升级）
sudo -u ckshop git clone <你的私有库地址> /opt/ck-shop-src
sudo -u ckshop rsync -a --exclude '.git' /opt/ck-shop-src/ /opt/ck-shop/

# B. 本机打包上传（仓库已有 .gitignore，密钥不会被带上去）
rsync -avz --exclude node_modules --exclude .git --exclude '.output' \
  ./ user@server:/opt/ck-shop/
```

---

## 4. 数据库初始化

顺序不能换（依据 `ck-shop-schema-baseline.sql` 文件头说明：基础库脚本只建 `sys_*` 等系统表，商城 34 张表在这个基线脚本里）。

```bash
cd /opt/ck-shop/ck-server

# 1) JeecgBoot 基础库（含 flyway_schema_history 记录，约 6.8MB）
mysql -uroot -p < db/ck-server-mysql-5.7.sql

# 2) 商城表基线（34 张 ck_shop_* + sk_shop_product_tag_relation，全部 IF NOT EXISTS，可重复执行）
mysql -uroot -p ck-server < db/ck-shop-schema-baseline.sql

# 3) 基线之后新增的脚本（按 db/*.sql 文件头日期/newest 顺序补；全新库通常只需前两步）
#    db/ck-shop-analytics.sql、db/ck-shop-address-region.sql 等
```

建应用账号（口令写进 `ck-server/.env`）：

```sql
CREATE USER 'ckshop'@'127.0.0.1' IDENTIFIED BY '换成强口令';
CREATE USER 'ckshop'@'localhost' IDENTIFIED BY '换成强口令';
GRANT ALL PRIVILEGES ON `ck-server`.* TO 'ckshop'@'127.0.0.1';
GRANT ALL PRIVILEGES ON `ck-server`.* TO 'ckshop'@'localhost';
FLUSH PRIVILEGES;
```

校验：

```sql
USE `ck-server`;
SHOW TABLES LIKE 'ck_shop%';                 -- 应有 30+ 张
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

> **Flyway 说明**：dev profile 里 `spring.flyway.enabled=true`，但 prod profile 里是 `false`，且 `FlywayAutoConfiguration` 被排除（`application-prod.yml:108-110`）。也就是说**生产启动时不会自动跑增量迁移**，升级时要显式执行：
>
> ```bash
> cd /opt/ck-shop/ck-server/jeecg-module-system/jeecg-system-start
> mvn -Pprod flyway:info -Dflyway.url='jdbc:mysql://127.0.0.1:3306/ck-server' \
>     -Dflyway.user=ckshop -Dflyway.password='***' \
>     -Dflyway.locations=filesystem:./src/main/resources/flyway/sql/mysql \
>     -Dflyway.ignoreMigrationPatterns='*:missing'
> # 确认 pending 后再 flyway:migrate；历史表对不上先用 db/fix-flyway-history-from-3.9.2.1.sql 修
> ```

---

## 5. 后端 ck-server

### 5.1 打包

```bash
cd /opt/ck-shop/ck-server
mvn -Pprod -pl jeecg-module-system/jeecg-system-start -am -DskipTests clean package
```

三个参数都不能省（[backend-runbook.md](./backend-runbook.md) §1、§3 踩过坑）：

| 参数 | 省掉的后果 |
|---|---|
| `-Pprod` | 默认 profile 是 `dev`，`application.yml` 的 `@profile.name@` 被替换成 `dev` → 上线跑的是**开发配置**：模拟支付网关可用（`MockPaymentGateway` 是 `@Profile("dev")`）、Flyway 自动迁移、Swagger 暴露 |
| `-am` | 从本地仓库取**陈旧**的 `ck-shop` 制品塞进 fat jar → 接口 404、会话全判未登录 |
| `clean` | `target/classes` 里已删源码的残留被打进 jar → 启动失败（`Could not resolve type alias`） |

产物：`jeecg-module-system/jeecg-system-start/target/jeecg-system-start-3.9.5.jar`。

```bash
install -m 640 -o ckshop -g ckshop \
  jeecg-module-system/jeecg-system-start/target/jeecg-system-start-3.9.5.jar \
  /opt/ck-shop/ck-server/
```

> 打包需要能访问 `https://mvnrep.cikian.cn/repository/public/`（`pom.xml:104-117` 的 `CikianMaven`，验证码 SDK / crydis / cik-oss 等私有依赖）。内网构建不通时，在 `~/.m2/settings.xml` 配私服镜像或认证。

### 5.2 生产配置：必须补齐的 3 类缺口

`application-prod.yml` 是从上游 JeecgBoot 继承的，**直接拿它上线会出问题**。用「外部覆盖配置」补齐最省事（不用改仓库文件，也不怕以后合并上游）：

- 路径：`/opt/ck-shop/ck-server/config/application-prod.yml`（Spring Boot 会加载 `./config/` 下的同名 profile 文件，优先级高于 jar 内 classpath）
- 密钥仍然只放在 `/opt/ck-shop/ck-server/.env`（`application.yml:34-46` 已经配好 `spring.config.import`，支持 properties 语法）

```yaml
# /opt/ck-shop/ck-server/config/application-prod.yml
server:
  port: 9951                 # 与 nuxt-shop/.env 的 API_BASE_URL 对齐（jar 内默认 8080）
  servlet:
    context-path: /api

spring:
  datasource:
    druid:
      stat-view-servlet:
        enabled: false       # prod 已关；再显式写一遍，避免模板被改回去
    dynamic:
      datasource:
        master:              # jar 内是 127.0.0.1:3306/ck-server root/root，必须覆盖
          url: jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}?characterEncoding=UTF-8&useUnicode=true&useSSL=false&tinyInt1isBit=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
          username: ${MYSQL_USERNAME}
          password: ${MYSQL_PASSWORD}
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}
      database: ${REDIS_DATABASE:0}
  mail:
    host: ${MAIL_HOST:}
    port: ${MAIL_PORT:465}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail.smtp.ssl.enable: true
      mail.smtp.ssl.trust: ${MAIL_HOST:}
      mail.smtp.ssl.checkserveridentity: false

# ★ prod 里完全缺失的一块：首页轮播/店铺装修走 Mongo
mongo-plus:
  data:
    mongodb:
      host: ${MONGO_HOST}
      port: ${MONGO_PORT}
      database: ${MONGO_DATABASE}
      username: ${MONGO_USERNAME}
      password: ${MONGO_PASSWORD}
      authenticationDatabase: ck-saas
      connectTimeoutMS: 50000

jeecg:
  # 与 dev 一致：项目实际使用 cikoss（CommonConstant.UPLOAD_TYPE_CIKOSS）；prod 模板里是 alioss
  uploadType: cikoss
  domainUrl:
    pc: https://admin.example.com
    app: https://admin.example.com
  path:
    upload: /opt/ck-shop/upload
    webapp: /opt/ck-shop/webapp
  jmreport:
    signatureSecret: ${JMREPORT_SIGNATURE_SECRET}   # prod 模板缺这一项，积木报表要用

ck:
  oss:                       # 商品图片 / 附件（又拍云）
    enable: true
    provider: upyun
    endpoint: ${CK_OSS_ENDPOINT}
    access-key: ${CK_OSS_ACCESS_KEY}
    secret-key: ${CK_OSS_SECRET_KEY}
    bucket: ${CK_OSS_BUCKET}
    expire: 3600
    object-dir-prefix: ${CK_OSS_OBJECT_DIR_PREFIX:ck-saas}
    api-domain: TELECOM
  shop:
    mail:
      from: ${MAIL_USERNAME:}    # prod 模板缺这一项，店铺端验证码邮件发件人
```

> 对比依据：dev 有、prod 无的配置键共 37 个，主要是 `mongo-plus.*`、`ck.shop.mail.from`、`spring.mail.properties.mail.smtp.ssl.*`、`jeecg.jmreport.signatureSecret`、`ck.shop.pay.mock.*`（prod 不需要）。
> 若你更愿意直接改仓库里的 `application-prod.yml`，把那几处的字面值换成 `${MYSQL_*}` 之类的占位符也一样，只是以后合并上游时要重新处理。

### 5.3 后端 `.env`（生产必填）

```bash
cp /opt/ck-shop/ck-server/.env.example /opt/ck-shop/ck-server/.env
chmod 600 /opt/ck-shop/ck-server/.env
```

| 分组 | 必填 | 说明 |
|---|---|---|
| `MYSQL_HOST/PORT/DATABASE/USERNAME/PASSWORD` | ✅ | 缺失直接 `Could not resolve placeholder` 启动失败 |
| `REDIS_HOST/PORT/PASSWORD/DATABASE` | ✅ | 登录态、锁、验证码 ticket |
| `MONGO_HOST/PORT/DATABASE/USERNAME/PASSWORD` | ✅ | 首页装修 |
| `JEECG_SIGNATURE_SECRET` | ✅ | **必须**等于店铺端 `SIGN_SECRET`；管理端还要对上源码常量（§6.1） |
| `JEECG_AES_KEY` / `JEECG_AES_IV` | ✅ | **必须**等于店铺端 `AES_KEY/AES_IV`、管理端 `VITE_AES_KEY/VITE_AES_IV`。不一致的典型症状：登录一直提示"用户名或密码错误" |
| `CK_PRICE_TOKEN_SECRET` | ✅ | 非 dev 留空直接启动失败；多实例必须一致 |
| `CK_SHOP_CAPTCHA_SECRET` | ✅ | CikVerify 服务端密钥，≥32 位 |
| `CK_PAY_DEFAULT_CHANNEL` | ✅ | `WECHAT` / `ALIPAY`（`MOCK` 只在 dev 可用），留空发起支付直接报错 |
| `CK_OSS_*` / `MAIL_*` / `ALIYUN_OSS_*` / `MINIO_*` | 按需 | 图片上传、邮件验证码 |
| `GOOGLE_CLIENT_ID/SECRET` | 按需 | 店铺端 Google 登录；留空则前端自动禁用该按钮 |
| `PGVECTOR_*` / `AI_*` / `BRAVE_SEARCH_API_KEY` | 按需 | AI RAG 知识库 |
| `DRUID_STAT_*` / `KNIFE4J_*` | 建议 | prod 已关闭 Druid 面板，改强口令并保持关闭 |

**不要**在 `.env` 里写 `export`、不要给值加引号（它是 properties 语法，注释必须独占一行，见 `.env.example` 头部说明）。

### 5.4 systemd 托管

`/etc/systemd/system/ck-server.service`：

```ini
[Unit]
Description=ck-shop backend (JeecgBoot / ck-server)
After=network.target mysql.service redis-server.service mongod.service

[Service]
Type=simple
User=ckshop
Group=ckshop
# 关键：工作目录决定 Spring 能否找到 ./.env 和 ./config/application-prod.yml
WorkingDirectory=/opt/ck-shop/ck-server
Environment="JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64"
Environment="TZ=Asia/Shanghai"
ExecStart=/usr/bin/java -server -Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxMetaspaceSize=512m \
          -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8 \
          -jar /opt/ck-shop/ck-server/jeecg-system-start-3.9.5.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
LimitNOFILE=65536
StandardOutput=append:/opt/ck-shop/logs/ck-server.log
StandardError=append:/opt/ck-shop/logs/ck-server.err.log

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now ck-server
sudo systemctl status ck-server --no-pager
tail -f /opt/ck-shop/logs/ck-server.log | grep -E "Started JeecgSystemApplication|ERROR|Exception"
```

启动约 40 秒（懒加载在 prod 是关闭的）。就绪判据：

```bash
# 未带 token 时 Shiro 会拦住并返回 401 JSON —— 有响应就说明 Spring 起来了
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:9951/api/sys/user/list
curl -sS http://127.0.0.1:9951/api/sys/user/list | head -c 200
```

---

## 6. 管理端 admin-client

### 6.1 构建前必须改的 4 处

| # | 文件 | 改成 | 不改的后果 |
|---|---|---|---|
| 1 | `admin-client/.env.production` 的 `VITE_GLOB_API_URL` | `/api`（现在是 `/jeecgboot`） | 所有请求 404 |
| 2 | `admin-client/.env.production` 的 `VITE_GLOB_DOMAIN_URL` | `/api`（现在是 `http://127.0.0.1:8080/jeecg-boot`） | 上传/WebSocket 指向**用户本机** |
| 3 | `admin-client/src/utils/encryption/signMd5Utils.js:3` | 与后端 `JEECG_SIGNATURE_SECRET` 相同的值 | 签名校验失败，报"签名校验失败/invalid signature" |
| 4 | `admin-client/.env` 的 `VITE_AES_KEY` / `VITE_AES_IV`（或构建时注入同名环境变量） | 与后端 `JEECG_AES_KEY` / `JEECG_AES_IV` 相同 | 登录永远提示"用户名或密码错误" |

> ⚠️ `admin-client/.env` 和 `.env.production.local` 都在 `.gitignore` 里，**服务器上不会有**，必须自己创建：`cp .env.example .env` 后填 AES 密钥。直接用仓库里的 `.env.production` 会带上 `/jeecgboot` 和 `127.0.0.1:8080`。

更稳的做法是用不入库的覆盖文件，避免污染 `git status`：

```bash
# admin-client/.env.production.local（Vite 优先级高于 .env.production）
cat > /opt/ck-shop/admin-client/.env.production.local <<'EOF'
VITE_GLOB_API_URL = /api
VITE_GLOB_DOMAIN_URL = /api
VITE_GLOB_CAPTCHA_TYPE = SLIDER    # .env.production 里是 POW，这里覆盖成滑块
EOF
```

`VITE_GLOB_*` 会被写进 `dist/_app.config.js`，**发布后可直接改**；而 `VITE_AES_*`、`VITE_PUBLIC_PATH`、签名密钥是编译进 bundle 的，改必须重新打包。

> `VITE_GLOB_DOMAIN_URL` 写相对路径 `/api` 时，代码会自动拼成 `window.location.origin + '/api'`（`admin-client/src/hooks/setting/index.ts:64-75`），所以不需要写死域名，换域名也不用重打包。

### 6.2 构建与发布

```bash
cd /opt/ck-shop/admin-client
pnpm install --frozen-lockfile
pnpm build            # = vite build && postBuild(生成 dist/_app.config.js) && copyChat(复制 dist/chat)
```

产物 `dist/`：`index.html`、`assets/`、`js/`、`resource/`、`_app.config.js`、`chat/`、`sw.js`(PWA)。

```bash
sudo chown -R ckshop:ckshop /opt/ck-shop/admin-client/dist
sudo -u ckshop bash -c 'cd /opt/ck-shop/admin-client && pnpm build'
sudo chmod -R a+rX /opt/ck-shop/admin-client/dist      # 让 nginx(www-data) 可读
```

发布后想改接口地址、标题、验证码类型等，直接编辑 `dist/_app.config.js` 刷新即可（`VITE_GLOB_APP_SHORT_NAME` 决定 localStorage 前缀，改它会丢登录态，别动）。

> 构建期还有两处外部依赖：CikVerify 弹窗资源来自 `https://cdn.cap.cikian.cn`（`dist/index.html` 的 CSP 已放行），图标用本地内置（`VITE_GLOB_ICONIFY_USE_TYPE=local`）。若前端跑在无外网环境，验证码弹窗会空白。

---

## 7. 店铺端 nuxt-shop

### 7.1 生产 `.env`

```bash
cp /opt/ck-shop/nuxt-shop/.env.example /opt/ck-shop/nuxt-shop/.env
```

| 变量 | 必填 | 说明 |
|---|---|---|
| `API_BASE_URL` | ✅ | `http://127.0.0.1:9951/api`（与 §5.2 的 `server.port` 一致） |
| `STORE_CODE` | ✅ | 本站点店铺编码（后台【店铺管理】里维护）。留空时后端按域名兜底匹配；prod 的 `allow-default-store-fallback=false`，解析不到**直接报错** |
| `AES_KEY` / `AES_IV` / `SIGN_SECRET` | ✅ | 与后端 `JEECG_AES_KEY/JEECG_AES_IV/JEECG_SIGNATURE_SECRET` 一致；仍是开源默认值会**拒绝启动** |
| `NUXT_OG_IMAGE_SECRET` | ✅ | OG 图签名；由 `nuxt-og-image` 在**构建期**映射成 `runtimeConfig.ogImage.secret`，所以构建时必须在场 |
| `STORE_SITE_URL` | ✅ | 必须 `https://shop.example.com`；缺失或写成 localhost 时**构建直接失败**（防止 canonical/sitemap 指向 localhost） |
| `STORE_SITE_NAME` / `STORE_SITE_DESCRIPTION` | 建议 | 站名、SEO 描述 |
| `STORE_PUBLIC_ORIGIN` | ✅ | `https://shop.example.com`，Google OAuth 回调与 CSRF 同源判定用 |
| `COOKIE_SECURE` | ✅ | `true`（只有用 http 调试才设 false，否则浏览器拒收 Secure cookie，登录直接失效） |
| `RATE_LIMIT_REDIS_URL` | 建议 | 多实例部署才需要 `redis://:密码@127.0.0.1:6379/0`。⚠️ 这个变量在**构建期**决定 Nitro storage 的 driver（`nuxt.config.ts:226-230`），只在 systemd 里设会导致"以为用 Redis、实际写内存"，必须构建前就写进 `.env` |
| `NUXT_PUBLIC_GA4_ID` | 按需 | 不填则不加载 gtag |
| `ALLOW_INSECURE_*` | ❌ | 生产**不要**开（本地验构建产物才用） |

### 7.2 构建

**必须在服务器（x86_64 Linux）上重新构建**，仓库里现成的 `.output/` 是 Windows 产物：里面装的是 `@img/sharp-win32-x64`、`@takumi-rs/core-win32-x64-msvc`，而且把本地 `.env` 的 `storeCode`、`publicOrigin=http://127.0.0.1…`、`cookieSecure=false` 一并烧进了产物 —— 拷到 Ubuntu 上既跑不起来也不安全。

```bash
cd /opt/ck-shop/nuxt-shop
npm ci                 # 仓库里是 package-lock.json，用 npm 不用 pnpm
npm run build          # 产物 .output/
```

`nuxt.config.ts` 在 **构建期** 就会校验 `STORE_SITE_URL`、`AES_KEY/AES_IV/SIGN_SECRET`，缺一个直接失败 —— 报错信息会告诉你缺哪项。构建还会预压缩 `/_nuxt` 的 gzip/brotli（`nitro.compressPublicAssets`），Nitro 自己按 `Accept-Encoding` 发 `.br`/`.gz`，所以**不需要**在 Nginx 上为店铺端开 `gzip_static`。

> **哪些值被烧进产物**：`runtimeConfig` 里由 `process.env.X` 取的那批（`apiBaseUrl`、`storeCode`、`storeSiteName`、`publicOrigin`、`cookieSecure`、`apiTimeoutMs`…）在构建时写死。运行时可以用 `NUXT_` 前缀覆盖（`NUXT_API_BASE_URL`、`NUXT_COOKIE_SECURE`、`NUXT_PUBLIC_ORIGIN`…），但 `STORE_CODE`/`STORE_PUBLIC_ORIGIN` 这类还是**重新构建更稳妥**。最省心的做法：构建与运行用同一份 `.env`，本文的 systemd 单元就是这么做的。
>
> 另外 `.output/` 里含有 AES/签名密钥（明文在 `nitro.mjs`），部署后建议 `chmod -R go-rwx /opt/ck-shop/nuxt-shop/.output`。

### 7.3 systemd 托管

`/etc/systemd/system/nuxt-shop.service`：

```ini
[Unit]
Description=ck-shop storefront (Nuxt 4 SSR + Nitro BFF)
After=network.target ck-server.service

[Service]
Type=simple
User=ckshop
Group=ckshop
WorkingDirectory=/opt/ck-shop/nuxt-shop
Environment=NODE_ENV=production
Environment=TZ=Asia/Shanghai
# 只监听回环：外部一律经 Nginx（BFF 只信任来自本机/内网的代理头）
Environment=PORT=3001
Environment=HOST=127.0.0.1
# 进程环境变量优先于 --env-file（Node 不会用文件覆盖已存在的变量），所以 .env 里的 PORT 不会盖掉这里
# --env-file-if-exists 需要 Node ≥ 22.9，而 nuxt 4.4.8 本身要求 ≥ 22.12
ExecStart=/usr/bin/node --env-file-if-exists=/opt/ck-shop/nuxt-shop/.env .output/server/index.mjs
Restart=always
RestartSec=5
MemoryMax=2G
StandardOutput=append:/opt/ck-shop/logs/nuxt-shop.log
StandardError=append:/opt/ck-shop/logs/nuxt-shop.err.log

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now nuxt-shop
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:3001/en    # 200
```

> 端口/监听地址由 Nitro 直接读 `NITRO_PORT || PORT`、`NITRO_HOST || HOST`（`.output/server/index.mjs:22-23`）。
> 首页默认语言是 `en`，受支持前缀为 `/en`、`/zh`、`/fr`（`types/store-locale.ts`），健康检查用 `/en` 最稳。

---

## 8. Nginx 反代

### 8.1 全局：WebSocket upgrade 映射

`/etc/nginx/conf.d/00-upgrade.conf`（`map` 必须放在 `http` 上下文）：

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}
```

### 8.2 店铺前台 `shop.example.com`

`/etc/nginx/sites-available/shop.example.com.conf`：

```nginx
upstream ck_shop_web {
    server 127.0.0.1:3001;
    keepalive 32;
}

server {
    listen 80;
    server_name shop.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;          # nginx 1.24：用这行；nginx ≥1.25 也可写 listen 443 ssl; + http2 on;
    server_name shop.example.com;

    ssl_certificate     /etc/letsencrypt/live/shop.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/shop.example.com/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_session_cache   shared:SSL:10m;
    ssl_session_timeout 1d;

    server_tokens off;
    client_max_body_size 20m;      # 头像上传后端硬限 8MB（avatar/upload.post.ts:21），留足余量

    gzip on;
    gzip_comp_level 5;
    gzip_min_length 1024;
    gzip_vary on;
    gzip_proxied any;
    gzip_types text/plain text/css application/javascript application/json application/xml image/svg+xml;

    # ---- 转发头：BFF 的 CSRF / OAuth / 真实 IP 全靠这几个 ----
    proxy_http_version 1.1;
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;   # 缺它 → 写操作 403「请求来源校验失败」，HSTS 也会失效
    proxy_set_header X-Forwarded-Host  $host;
    # ★ 清掉浏览器自带的 X-Client-IP：BFF 优先采信这个头（clientIp.ts:71-73），不清就能伪造 IP 绕过限流
    proxy_set_header X-Client-IP       "";
    proxy_set_header Connection        "";        # 配合 upstream keepalive
    proxy_connect_timeout 5s;
    proxy_send_timeout    60s;
    proxy_read_timeout    60s;      # > BFF 自己的超时（见 §11「下单偶发 504」）

    # 带 hash 的构建产物：一年不可变（Nitro 本身也会下发同样的 Cache-Control）
    location /_nuxt/ {
        proxy_pass http://ck_shop_web;
        add_header Cache-Control "public, max-age=31536000, immutable";
    }

    # 其余全交给 Nitro：页面 SSR、/api/*（BFF）、sitemap.xml、robots.txt、/_og/** OG 图
    location / {
        proxy_pass http://ck_shop_web;
        # SSR 是非流式渲染，开/关 buffering 都不影响正确性；关掉让首字节更早到浏览器
        proxy_buffering off;
    }
}
```

**不要给 `/api/` 加 `proxy_cache`**：`nuxt.config.ts:284-302` 已经用 `routeRules` 的 SWR 把可缓存的公开读接口处理掉了，而 `/api/img` 明确禁止缓存（曾经因为缓存把 Buffer 序列化成 JSON，导致"刷新后图片全裂"）。OG 图走 `/_og/**` 且 URL 带签名 query，反代时**不要丢掉 query string**（`proxy_pass` 默认保留，别加 `?`）。

### 8.3 管理后台 `admin.example.com`

`/etc/nginx/sites-available/admin.example.com.conf`：

```nginx
upstream ck_admin_api {
    server 127.0.0.1:9951;
    keepalive 32;
}

server {
    listen 80;
    server_name admin.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name admin.example.com;

    root  /opt/ck-shop/admin-client/dist;
    index index.html;

    ssl_certificate     /etc/letsencrypt/live/admin.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/admin.example.com/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;

    server_tokens off;
    client_max_body_size 20m;

    gzip on;
    gzip_comp_level 5;
    gzip_min_length 1024;
    gzip_vary on;
    gzip_proxied any;
    gzip_types text/plain text/css application/javascript application/json application/xml image/svg+xml;
    # admin-client 构建时已预生成 224 个 .gz（.env.production 的 VITE_BUILD_COMPRESS=gzip），
    # 打开后直接发预压缩文件，省掉运行时压缩的 CPU
    gzip_static on;

    # ---------- 后端 API：Spring 的 context-path 就是 /api，路径原样透传 ----------
    location /api/ {
        proxy_pass http://ck_admin_api;        # 末尾不能加 /api/，否则会变成 /api/api/...
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Client-IP       "";                 # 同上：禁止浏览器伪造客户端 IP
        proxy_set_header Upgrade           $http_upgrade;      # /api/websocket/*（顶栏通知、JVxeTable）
        proxy_set_header Connection        $connection_upgrade;
        proxy_connect_timeout 5s;
        proxy_send_timeout    300s;        # AI 对话、报表导出、大文件上传
        proxy_read_timeout    300s;
        proxy_request_buffering off;
    }

    # ---------- 前端静态资源（带 hash，可长缓存） ----------
    location ~ ^/(assets|js|resource)/ {
        expires 30d;
        add_header Cache-Control "public, max-age=2592000";
        try_files $uri =404;
    }

    # 运行时配置与入口：绝不缓存
    location = /_app.config.js { add_header Cache-Control "no-store"; try_files $uri =404; }
    location = /index.html    { add_header Cache-Control "no-store"; try_files $uri =404; }
    location = /sw.js         { add_header Cache-Control "no-store"; try_files $uri =404; }

    # ---------- SPA history 路由回退 ----------
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

启用：

```bash
sudo ln -s /etc/nginx/sites-available/shop.example.com.conf  /etc/nginx/sites-enabled/
sudo ln -s /etc/nginx/sites-available/admin.example.com.conf /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

### 8.4 HTTPS 证书

先按上面的 **80 端口版本** 上线（把 443 的 `server` 块和 `return 301` 暂时注释掉），再签证书：

```bash
sudo apt -y install certbot python3-certbot-nginx
sudo certbot --nginx -d shop.example.com -d admin.example.com
# 证书续期已由 systemd timer 自动处理，验证： sudo certbot renew --dry-run
```

`certbot --nginx` 会自动把 443 端口、证书路径和 http→https 跳转写进这两个 conf。签发完把本文 §8.2/§8.3 里的 `location`、缓存、超时、转发头**补回 443 的 `server` 块**，再 `nginx -t && systemctl reload nginx`。

> `STORE_SITE_URL` 必须是 https 且非 localhost，否则 `nuxt-shop` 构建阶段直接失败 —— 所以证书要在第一次构建店铺端之前签好（或先用 `ALLOW_INSECURE_SITE_URL=true` 本地验产物）。

### 8.5 安全组 / 防火墙

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80,443/tcp
sudo ufw --force enable
sudo ufw status verbose
```

再确认这些端口**只监听回环**：

```bash
ss -lntp | grep -E ':(9951|3001|3306|6379|27017)'
# 期望全部是 127.0.0.1:xxxx（Java 如果显示 *:9951，在覆盖配置里加 server.address: 127.0.0.1）
```

---

## 9. 全链路验收清单

```bash
# 1) 基础设施
systemctl is-active mysql redis-server mongod nginx ck-server nuxt-shop

# 2) 后端直连（401 = 已就绪）
curl -sS -o /dev/null -w 'backend  %{http_code}\n' http://127.0.0.1:9951/api/sys/user/list

# 3) 店铺端 SSR 直连
curl -sS -o /dev/null -w 'nuxt     %{http_code}\n' http://127.0.0.1:3001/en
curl -sS http://127.0.0.1:3001/en | grep -o '<title>[^<]*</title>'

# 4) 经 Nginx
curl -sS -o /dev/null -w 'shop     %{http_code}\n'  https://shop.example.com/en
curl -sS -o /dev/null -w 'admin    %{http_code}\n'  https://admin.example.com/
curl -sS -o /dev/null -w 'admin-api %{http_code}\n' https://admin.example.com/api/sys/user/list
curl -sS -o /dev/null -w 'sitemap  %{http_code}\n'  https://shop.example.com/sitemap.xml
curl -sS -o /dev/null -w 'robots   %{http_code}\n'  https://shop.example.com/robots.txt
curl -sS -o /dev/null -w 'config   %{http_code}\n'  https://admin.example.com/_app.config.js

# 5) 浏览器侧（必须人工做）
#    · 店铺端：打开 https://shop.example.com/en → DevTools Network 里资源全是 https 同源、无 4xx
#    · 管理端：登录（要过 CikVerify 滑块）→ 商品/订单/店铺管理页能出数据
#    · 店铺端登录 → 刷新页面仍是登录态（验证 cookie Secure + X-Forwarded-Proto）
#    · 店铺端加购物车/下单 → 不出现 403「请求来源校验失败」
```

---

## 10. 升级与回滚

```bash
# 升级前备份
TS=$(date +%F-%H%M)
mysqldump --single-transaction --routines --triggers -uroot -p ck-server > /opt/ck-shop/backup/db-$TS.sql
mongodump --db ck-saas --out /opt/ck-shop/backup/mongo-$TS
tar czf /opt/ck-shop/backup/artifacts-$TS.tgz \
  /opt/ck-shop/ck-server/jeecg-system-start-3.9.5.jar \
  /opt/ck-shop/nuxt-shop/.output /opt/ck-shop/admin-client/dist

# 升级
cd /opt/ck-shop && sudo -u ckshop git pull
#   → 后端：§5.1 重新打包 → sudo systemctl restart ck-server
#   → 管理端：§6.2 重新构建（dist 由 Nginx 直接读，无需重启）
#   → 店铺端：§7.2 重新构建 → sudo systemctl restart nuxt-shop
#   → 有新增 Flyway 脚本：先按 §4 的 flyway:migrate 跑迁移，再重启后端

# 回滚
sudo systemctl stop ck-server
cp /opt/ck-shop/backup/...-3.9.5.jar.bak /opt/ck-shop/ck-server/jeecg-system-start-3.9.5.jar
sudo systemctl start ck-server
# 数据库回滚：mysql -uroot -p ck-server < /opt/ck-shop/backup/db-$TS.sql
```

---

## 11. 故障排查

| 现象 | 最可能原因 | 处理 |
|---|---|---|
| `shop.example.com` 502 | `nuxt-shop` 没起 / 端口不是 3001 | `journalctl -u nuxt-shop -n 100`、`ss -lntp \| grep 3001` |
| 页面 504 | 后端慢或挂住 | 先看 `ck-server` 日志与 `ss -lntp \| grep 9951` |
| **下单/支付偶发 504（与部署无关）** | BFF 统一超时判断有缺陷：`apiClient.ts:151-154` 用了一个不存在的 `options.method`，恒走 GET 分支 → 所有请求（含 POST）都是 **3s** 超时，`API_TIMEOUT_MS=10000` 实际不生效 | 要么提高后端响应速度，要么修 `createApiClient` 的 options 类型并传入 `method`；Nginx 侧把 `proxy_read_timeout` 调大也救不了 BFF 自己先超时 |
| 店铺端写操作全部 403「请求来源校验失败」 | Nginx 少配 `X-Forwarded-Proto` / `Host` | 按 §8.2 补转发头 |
| 店铺端登录成功但刷新就掉登录 | `COOKIE_SECURE=true` 却用 http 访问；或 Nginx 没透传 `X-Forwarded-Proto` | 全站强制 https；补转发头 |
| 管理端登录一直"用户名或密码错误" | `VITE_AES_KEY/IV` ≠ 后端 `JEECG_AES_KEY/IV`；或后端 `JEECG_AES_KEY` 为空 | 改 `admin-client/.env` 重新打包 |
| 管理端接口 404 | `VITE_GLOB_API_URL` 还是 `/jeecgboot`；或 Nginx 没配 `/api/` | 改 `_app.config.js`（可运行时改）或重打包 |
| 管理端报签名校验失败 | `signMd5Utils.js` 常量 ≠ 后端 `JEECG_SIGNATURE_SECRET` | 改源码重新打包（或把后端密钥改成与常量一致） |
| 店铺端报签名失败 / 401 | `nuxt-shop/.env` 的 `SIGN_SECRET` ≠ `JEECG_SIGNATURE_SECRET` | 两边改成同一个值，重启 |
| 店铺端启动即失败，提示缺 `AES_KEY`/`STORE_SITE_URL` | `.env` 未填或仍是示例值 | 按 §7.1 补齐（这是故意的 fail-fast） |
| 首页轮播/店铺装修 500 | prod 缺 `mongo-plus` 配置或 Mongo 认证库不对 | 按 §5.2 补配置；账号建在 `ck-saas` 库 |
| 商品图片裂 | 又拍云 `CK_OSS_*` 未配/过期；或给 `/api` 加了 `proxy_cache` | 检查 `.env`；删除 `/api` 的缓存配置 |
| 后台图标空白 | `VITE_GLOB_ICONIFY_USE_TYPE=online` 且服务器/浏览器无外网 | 改回 `local` 重打包 |
| 验证码弹窗空白 | 浏览器访问不到 `https://cdn.cap.cikian.cn`；或后端到 `https://cap.cikian.cn` 不通 | 放通出网；`curl -I https://cap.cikian.cn` |
| 上传 413 | `client_max_body_size` 太小 | 调大并 `reload` |
| 打包/构建拉不到私有依赖 | `mvnrep.cikian.cn`（Maven）或 `@cikian/*`（npm）不可达 | 配私服/认证；或离线上传 `~/.m2/repository`、`node_modules` |
| 定时任务/验证码邮件不发 | `MAIL_*` 缺失或 `MAIL_USERNAME` 不是完整邮箱 | 按 `.env.example` 注释配（465=SSL） |
| 直接拷现成的 `.output` 到 Ubuntu，启动报 `Cannot find module '@img/sharp-linux-x64'` / OG 图 500 | 仓库里的 `.output` 是 Windows 构建产物（`@img/sharp-win32-x64`、`@takumi-rs/core-win32-x64-msvc`） | 在 Linux 上 `npm ci && npm run build` 重新构建，不能跨平台拷贝 |
| 店铺端限流看起来"没生效/各实例不一致" | `RATE_LIMIT_REDIS_URL` 只在构建期决定 storage driver | 构建前写进 `.env`，再重启 |

---

## 12. 附：配置改动生效方式速查

| 配置 | 文件 | 生效方式 |
|---|---|---|
| 后端数据库/Redis/Mongo/密钥 | `ck-server/.env` | 重启 `ck-server` |
| 后端端口、context-path、上传目录 | `ck-server/config/application-prod.yml` | 重启 `ck-server` |
| 管理端接口地址、标题、验证码类型 | `admin-client/dist/_app.config.js` | 改完刷新浏览器 |
| 管理端 AES 密钥、签名密钥、发布路径 | 源码 / `.env`（`VITE_AES_*`、`signMd5Utils.js`、`VITE_PUBLIC_PATH`） | **重新构建** |
| 店铺端后端地址、店铺编码、域名 | `nuxt-shop/.env` | 重启 `nuxt-shop`（`apiBaseUrl` 等构建期取值如需变更请重建） |
| 店铺端 SEO/OG/sitemap | `nuxt-shop/.env` 的 `STORE_SITE_URL` 等 | **重新构建**（构建期校验） |
| Nginx 路由/缓存/超时 | `sites-available/*.conf` | `nginx -t && systemctl reload nginx` |

> **关于 Docker**：根目录 `docker-compose.yml` / `docker-compose-cloud.yml` 是上游 JeecgBoot 遗留，构建上下文指向本仓库不存在的 `./jeecg-boot/`、`./jeecgboot-vue3/`，**当前不可用**（文件头已注明）。要用容器化请先重写 compose 的上下文与镜像，本指南不依赖它。
