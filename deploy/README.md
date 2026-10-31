# 部署执行清单（Ubuntu 24.04 · 单机）

> 本目录是「可直接拷到服务器」的配置成品：systemd 单元、Nginx 站点、后端生产覆盖配置、两个前端的 `.env` 模板。
> 背景与取舍说明见 [docs/部署与Nginx反代指南-Ubuntu24.md](../docs/部署与Nginx反代指南-Ubuntu24.md)（下称《指南》）。
> 目标形态：`shop.example.com` → Node(3001) → Java(9951)；`admin.example.com` → 静态 dist + `/api/` → Java(9951)。
> 全文把 `shop.example.com` / `admin.example.com` / 口令 / 密钥替换成你自己的值。

## 0. 文件放置对照表

| 本目录文件 | 服务器目标路径 |
|---|---|
| `systemd/ck-server.service` | `/etc/systemd/system/ck-server.service` |
| `systemd/nuxt-shop.service` | `/etc/systemd/system/nuxt-shop.service` |
| `nginx/00-upgrade.conf` | `/etc/nginx/conf.d/00-upgrade.conf` |
| `nginx/shop.example.com.conf` | `/etc/nginx/sites-available/shop.example.com.conf`（再 `ln -s` 到 `sites-enabled/`） |
| `nginx/admin.example.com.conf` | `/etc/nginx/sites-available/admin.example.com.conf`（再 `ln -s`） |
| `ck-server/config/application-prod.yml` | `/opt/ck-shop/ck-server/config/application-prod.yml` |
| `admin-client/.env` | `/opt/ck-shop/admin-client/.env` |
| `admin-client/.env.production.local` | `/opt/ck-shop/admin-client/.env.production.local` |
| `nuxt-shop/.env` | `/opt/ck-shop/nuxt-shop/.env` |

目录约定：

```
/opt/ck-shop/
├── ck-server/       # jar + .env + config/application-prod.yml
├── nuxt-shop/       # 源码 + .env + .output
├── admin-client/    # 源码 + .env* + dist
├── upload/ webapp/  # 后端本地存储（uploadType=local 时才用）
├── logs/  backup/
```

## 1. 一次性准备（装依赖 + 建用户 + 建目录）

```bash
sudo apt update && sudo apt -y install curl gnupg git unzip openjdk-17-jdk-headless maven nginx mysql-server redis-server
# Node ≥ 22.12（nuxt 4.4.8 engines: ^22.12.0 || ^24.11.0）
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash - && sudo apt -y install nodejs
sudo npm i -g pnpm@9

# MongoDB 8（首页轮播等文档数据必需；application-prod.yml 里没有这段配置，靠 overlay 补）
curl -fsSL https://www.mongodb.org/static/pgp/server-8.0.asc | sudo gpg -o /usr/share/keyrings/mongodb-server-8.0.gpg --dearmor
echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-8.0.gpg ] https://repo.mongodb.org/apt/ubuntu noble/mongodb-org/8.0 multiverse" \
  | sudo tee /etc/apt/sources.list.d/mongodb-org-8.0.list
sudo apt update && sudo apt -y install mongodb-org

# 运行用户与目录
sudo useradd -r -m -s /bin/bash ckshop
sudo mkdir -p /opt/ck-shop/{logs,backup,upload,webapp}
sudo chown -R ckshop:ckshop /opt/ck-shop
sudo mkdir -p /var/www/certbot
```

低内存机器（2C4G）先加 swap，否则 `mvn package` / `nuxt build` 容易被 OOM kill：

```bash
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 2. 数据库（MySQL / Redis / MongoDB）

```bash
# MySQL 字符集与时区
sudo tee /etc/mysql/mysql.conf.d/zz-ck-shop.cnf >/dev/null <<'EOF'
[mysqld]
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci
max_allowed_packet = 128M
explicit_defaults_for_timestamp = 1
default-time-zone = '+08:00'
max_connections = 500
[client]
default-character-set = utf8mb4
EOF
sudo systemctl restart mysql

# 建库 + 建账号（库名用反引号，中间是连字符）
sudo mysql <<'SQL'
CREATE DATABASE IF NOT EXISTS `ck-server` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'ckshop'@'127.0.0.1' IDENTIFIED BY '换成强口令';
CREATE USER IF NOT EXISTS 'ckshop'@'localhost' IDENTIFIED BY '换成强口令';
GRANT ALL PRIVILEGES ON `ck-server`.* TO 'ckshop'@'127.0.0.1';
GRANT ALL PRIVILEGES ON `ck-server`.* TO 'ckshop'@'localhost';
FLUSH PRIVILEGES;
SQL

# Redis：设密码 + 只监听本机（Ubuntu 默认 bind 是 "127.0.0.1 -::1"，- 前缀表示 IPv6 不可用时不报错）
sudo sed -i 's/^# *requirepass .*/requirepass 换成强口令/' /etc/redis/redis.conf
sudo sed -i 's/^bind .*/bind 127.0.0.1 -::1/' /etc/redis/redis.conf
sudo systemctl restart redis-server

# MongoDB：先建库账号（此时鉴权还没开），再打开鉴权
mongosh --quiet <<'JS'
use ck-saas
db.createUser({ user: "ck-saas", pwd: "换成强口令", roles: [{ role: "readWrite", db: "ck-saas" }] })
JS
sudo sed -i 's/^  bindIp:.*/  bindIp: 127.0.0.1/' /etc/mongod.conf
# mongod.conf 默认没有 security 段，直接追加（已有则跳过）
sudo grep -q '^security:' /etc/mongod.conf \
  || printf '\nsecurity:\n  authorization: enabled\n' | sudo tee -a /etc/mongod.conf
sudo systemctl restart mongod
```

## 3. 后端 ck-server（一个后端）

```bash
# 3.1 放代码
sudo -u ckshop git clone <你的私有库地址> /opt/ck-shop-src
sudo -u ckshop rsync -a --exclude '.git' /opt/ck-shop-src/ /opt/ck-shop/

# 3.2 导入库表（顺序不能换：先基础库，再商城基线）
#     用 root 导入：基础库脚本里带 CREATE DATABASE，普通账号没有该权限
cd /opt/ck-shop/ck-server
sudo mysql < db/ck-server-mysql-5.7.sql
sudo mysql ck-server < db/ck-shop-schema-baseline.sql
sudo mysql -e "SHOW TABLES FROM \`ck-server\` LIKE 'ck_shop%'"   # 应有 30+ 张

# 3.3 打包（-Pprod / -am / clean 三个都不能省）
mvn -Pprod -pl jeecg-module-system/jeecg-system-start -am -DskipTests clean package
install -m 640 -o ckshop -g ckshop \
  jeecg-module-system/jeecg-system-start/target/jeecg-system-start-3.9.5.jar \
  /opt/ck-shop/ck-server/

# 3.4 生产配置：覆盖文件（补 db/redis/mongo/mail/上传目录）
sudo -u ckshop mkdir -p /opt/ck-shop/ck-server/config
sudo -u ckshop cp /opt/ck-shop/deploy/ck-server/config/application-prod.yml /opt/ck-shop/ck-server/config/
#    ↑ deploy/ 目录随代码一起被 rsync 上去了；没带就把本仓库的该文件 scp 过去

# 3.5 密钥（唯一的敏感配置来源，服务端 properties 语法，不加引号、不写 export）
sudo -u ckshop cp /opt/ck-shop/ck-server/.env.example /opt/ck-shop/ck-server/.env
sudo -u ckshop nano /opt/ck-shop/ck-server/.env    # 至少填：MYSQL_*/REDIS_*/MONGO_*/JEECG_*/CK_*/MAIL_*
sudo chmod 600 /opt/ck-shop/ck-server/.env

# 3.6 托管 + 启动
sudo cp /opt/ck-shop/deploy/systemd/ck-server.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now ck-server
sudo systemctl status ck-server --no-pager
tail -f /opt/ck-shop/logs/ck-server.log

# 3.7 验证：返回 401（Shiro 拦截）就说明起来了
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:9951/api/sys/user/list
```

## 4. 管理端 admin-client（前端一：纯静态）

```bash
cd /opt/ck-shop/admin-client

# 4.1 两个 env：一个放 AES 密钥，一个把接口地址改成 /api
sudo -u ckshop cp /opt/ck-shop/deploy/admin-client/.env .env
sudo -u ckshop cp /opt/ck-shop/deploy/admin-client/.env.production.local .env.production.local
sudo -u ckshop nano .env     # VITE_AES_KEY / VITE_AES_IV 必须等于后端 JEECG_AES_KEY / JEECG_AES_IV

# 4.2 ★ 签名密钥：前端是硬编码的，必须与后端 JEECG_SIGNATURE_SECRET 一致
sudo -u ckshop nano src/utils/encryption/signMd5Utils.js
#   const signatureSecret = '<改成与后端一致的值>';

# 4.3 构建
sudo -u ckshop pnpm install --frozen-lockfile
sudo -u ckshop pnpm build
#   产物：dist/{index.html,assets,js,resource,chat,_app.config.js,...}

# 4.4 让 nginx 可读
sudo chown -R ckshop:ckshop dist && sudo chmod -R a+rX dist
```

发布后要改接口地址/标题/验证码类型，直接编辑 `dist/_app.config.js` 即可（刷新生效），
但 AES 密钥、签名密钥、`VITE_PUBLIC_PATH` 是编译进 bundle 的，改了必须重打包。

## 5. 店铺端 nuxt-shop（前端二：SSR + BFF）

```bash
cd /opt/ck-shop/nuxt-shop

# 5.1 env：构建期就要校验，必须先填好
sudo -u ckshop cp /opt/ck-shop/deploy/nuxt-shop/.env .env
sudo -u ckshop nano .env
#   API_BASE_URL=http://127.0.0.1:9951/api
#   STORE_CODE=<后台店铺管理里的编码>
#   AES_KEY/AES_IV/SIGN_SECRET 与后端一致
#   STORE_SITE_URL=https://shop.example.com（必须是 https 非 localhost）
#   STORE_PUBLIC_ORIGIN=https://shop.example.com
#   COOKIE_SECURE=true

# 5.2 ★ 必须在 Linux 上重新构建：仓库里的 .output 是 Windows 产物
#     （@img/sharp-win32-x64、@takumi-rs/core-win32-x64-msvc，且烧死了本地 env）
sudo -u ckshop npm ci
sudo -u ckshop npm run build          # 产物 .output/
sudo chmod -R go-rwx .output          # 产物里含 AES/签名密钥

# 5.3 托管 + 启动
sudo cp /opt/ck-shop/deploy/systemd/nuxt-shop.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now nuxt-shop
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:3001/en    # 200
```

## 6. Nginx（两个站点）

```bash
# 6.1 证书（先签，再起完整配置；standalone 方式需要 nginx 此刻未占用 80）
sudo apt -y install certbot
sudo systemctl stop nginx || true
sudo certbot certonly --standalone -d shop.example.com
sudo certbot certonly --standalone -d admin.example.com
#   续期自动（systemd timer），验证： sudo certbot renew --dry-run

# 6.2 站点配置
sudo cp /opt/ck-shop/deploy/nginx/00-upgrade.conf /etc/nginx/conf.d/
sudo cp /opt/ck-shop/deploy/nginx/shop.example.com.conf /etc/nginx/sites-available/
sudo cp /opt/ck-shop/deploy/nginx/admin.example.com.conf /etc/nginx/sites-available/
sudo ln -sf /etc/nginx/sites-available/shop.example.com.conf  /etc/nginx/sites-enabled/
sudo ln -sf /etc/nginx/sites-available/admin.example.com.conf /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
#   把两个 conf 里的域名/证书路径换成自己的
sudo nginx -t && sudo systemctl restart nginx

# 6.3 防火墙
sudo ufw allow OpenSSH && sudo ufw allow 80,443/tcp && sudo ufw --force enable
ss -lntp | grep -E ':(9951|3001|3306|6379|27017)'   # 期望全部 127.0.0.1
```

## 7. 验收（一条条敲）

```bash
systemctl is-active mysql redis-server mongod nginx ck-server nuxt-shop
curl -sS -o /dev/null -w 'backend  %{http_code}\n' http://127.0.0.1:9951/api/sys/user/list   # 401
curl -sS -o /dev/null -w 'nuxt     %{http_code}\n' http://127.0.0.1:3001/en                 # 200
curl -sS -o /dev/null -w 'shop     %{http_code}\n' https://shop.example.com/en              # 200
curl -sS -o /dev/null -w 'admin    %{http_code}\n' https://admin.example.com/               # 200
curl -sS -o /dev/null -w 'admin-api %{http_code}\n' https://admin.example.com/api/sys/user/list  # 401
curl -sS -o /dev/null -w 'sitemap  %{http_code}\n' https://shop.example.com/sitemap.xml
curl -sS -o /dev/null -w 'config   %{http_code}\n' https://admin.example.com/_app.config.js
```

浏览器侧必须人工过一遍：

1. 管理端登录（过 CikVerify 滑块）→ 商品/订单/店铺页有数据；
2. 店铺端能打开商品页、登录后**刷新仍是登录态**（验证 Secure cookie + `X-Forwarded-Proto`）；
3. 店铺端加购物车 / 下单不出现 403「请求来源校验失败」；
4. 首页轮播有图（验证 MongoDB 配好了）。

## 8. 日常升级 / 回滚

```bash
TS=$(date +%F-%H%M)
mysqldump --single-transaction --routines -uroot -p ck-server > /opt/ck-shop/backup/db-$TS.sql
mongodump --db ck-saas --out /opt/ck-shop/backup/mongo-$TS
tar czf /opt/ck-shop/backup/artifacts-$TS.tgz \
  /opt/ck-shop/ck-server/jeecg-system-start-3.9.5.jar /opt/ck-shop/nuxt-shop/.output /opt/ck-shop/admin-client/dist

cd /opt/ck-shop && sudo -u ckshop git pull
# 后端：重打包 → systemctl restart ck-server
# 管理端：pnpm build（nginx 直接读 dist，无需重启）
# 店铺端：npm ci && npm run build → systemctl restart nuxt-shop
# 新增 Flyway 脚本：先跑迁移（《指南》§4），再重启后端
```

回滚：用 `artifacts-$TS.tgz` 覆盖 jar / `.output` / `dist` 后重启对应服务；数据库回滚 `mysql -uroot -p ck-server < db-$TS.sql`。

## 9. 三个最容易漏的点

1. **三处密钥必须成对**：后端 `JEECG_AES_KEY/IV` ↔ 店铺端 `AES_KEY/IV` ↔ 管理端 `VITE_AES_KEY/IV`；后端 `JEECG_SIGNATURE_SECRET` ↔ 店铺端 `SIGN_SECRET` ↔ 管理端 `signMd5Utils.js` 常量。不一致分别表现为「登录永远用户名或密码错误」「签名校验失败」。
2. **后端必须 `-Pprod` 打包**，否则跑的是 dev 配置（模拟支付网关可用、Swagger 暴露、Flyway 会自动迁移）。
3. **店铺端 `.output` 不能跨平台拷贝**，必须在 Ubuntu 上 `npm ci && npm run build`。
