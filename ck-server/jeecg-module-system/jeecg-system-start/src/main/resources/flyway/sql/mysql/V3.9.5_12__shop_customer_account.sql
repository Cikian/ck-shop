-- =============================================================================
-- 店铺端账号体系（C 端买家）· 数据模型
-- 版本：3.9.5.12
--
-- 设计要点：
--   1. 店铺端与管理端**共用 sys_user 表**，靠「角色 + 部门」隔离前台买家：
--      买家账号挂 shop_customer 角色、sys_org_code 留空（不属于任何店铺部门）。
--      不新建 ck_shop_user 表 —— 收货地址按 sys_user.id 共享，拆表会把地址割裂。
--   2. register_store_code / register_store_id 只记录**注册来源店铺**（第一次从哪来）。
--      它是营销归因，不是数据隔离依据：买家可以跨店登录，隔离靠 store 维度业务表。
--   3. google_sub 独立列，**不复用 third_id/third_type** —— 那两个列是 JeecgBoot
--      原生第三方登录用的，历史数据里已有值，混用会出现"两个系统抢同一列"。
--      google_sub 加 UNIQUE：一个 Google 账号只能绑一个 ck-shop 账号。
--   4. pwd_set 解决"自动注册用户没有真实密码"的判定问题：
--      JwtUtil.sign 需要非空密码哈希，所以自动注册时写一个**无人知晓明文**的随机哈希；
--      此时 password 非空但用户并没有密码，必须靠 pwd_set 区分。
--      DEFAULT 1 对既有数据是正确的（老用户都有真实密码）。
--   5. ck_shop_login_log 记录**每次登录尝试**（含失败），用来做店铺维度的留存分析，
--      以及排查"某个账号为什么登不上"。它是流水表，只增不改。
--
--   6. 本脚本**刻意不写 DELIMITER / 存储过程**：JeecgBoot 的 Flyway 迁移目录里
--      全是纯 SQL 脚本，DELIMITER 是 mysql 客户端指令而非服务端语法，
--      放进来会让 Flyway 的 JDBC 执行失败（它会把整段当一个 statement 发）。
--
-- ⚠️ 已知约束（Lead 已裁决，勿"优化"）：
--    本脚本**不可重复执行**。MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，
--    而 PREPARE/EXECUTE 的动态 SQL 包装会把 20 行 DDL 变成 60 行"聪明代码"，
--    只为了覆盖"运维手工重跑"这种非正常场景，得不偿失。
--    Flyway 自身保证每个版本只执行一次，这就是唯一的幂等保障。
--    第 3) 节的 CREATE TABLE IF NOT EXISTS 是唯一一处无害的额外保护。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) sys_user 增量列（与契约第 1 节逐字一致）
-- ---------------------------------------------------------------------------

-- 注册来源店铺：记录用户第一次是从哪个店铺注册的
ALTER TABLE `sys_user`
  ADD COLUMN `register_store_code` varchar(64) NULL COMMENT '注册来源店铺编码'
      AFTER `iz_hide_contact`,
  ADD COLUMN `register_store_id` varchar(32) NULL COMMENT '注册来源店铺ID'
      AFTER `register_store_code`;

-- Google 账号唯一标识（复用已有 third_id/third_type 语义会与旧数据冲突，独立列出）
ALTER TABLE `sys_user`
  ADD COLUMN `google_sub` varchar(64) NULL COMMENT 'Google 账号唯一标识(sub)'
      AFTER `register_store_id`,
  ADD UNIQUE INDEX `uniq_sys_user_google_sub` (`google_sub`) USING BTREE;

-- 是否由用户设置过密码。
-- 自动注册（Google / 邮箱验证码首次登录）会写 0：password 字段有一个
-- **无人知道明文**的随机哈希（仅为了让 JwtUtil.sign 能签发 token），
-- 因此不能用 "password 是否为空" 判断用户有没有密码。
ALTER TABLE `sys_user`
  ADD COLUMN `pwd_set` tinyint(1) NULL DEFAULT 1
      COMMENT '是否由用户设置过密码: 1-是, 0-系统随机占位' AFTER `google_sub`;

-- ---------------------------------------------------------------------------
-- 2) 前台买家角色 shop_customer
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role`
  (id, role_name, role_code, description, create_time, update_time)
VALUES
  (REPLACE(UUID(),'-',''), '前台买家', 'shop_customer', '店铺端注册用户（C 端买家）', NOW(), NOW());

-- ---------------------------------------------------------------------------
-- 3) 登录来源流水表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ck_shop_login_log` (
  `id`            varchar(32)  NOT NULL COMMENT '主键',
  `user_id`       varchar(32)  NULL     COMMENT '用户ID',
  `username`      varchar(100) NULL     COMMENT '用户账号(冗余)',
  `email`         varchar(100) NULL     COMMENT '登录邮箱(冗余)',
  `login_type`    varchar(20)  NULL     COMMENT '登录方式: PASSWORD/EMAIL_CODE/GOOGLE',
  `store_code`    varchar(64)  NULL     COMMENT '登录来源店铺编码',
  `store_id`      varchar(32)  NULL     COMMENT '登录来源店铺ID',
  `client_ip`     varchar(64)  NULL     COMMENT '客户端IP',
  `user_agent`    varchar(500) NULL     COMMENT 'User-Agent',
  `login_time`    datetime     NULL     COMMENT '登录时间',
  `success`       tinyint(1)   NULL     COMMENT '是否成功: 1-成功, 0-失败',
  `fail_reason`   varchar(200) NULL     COMMENT '失败原因',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sll_user_id` (`user_id`) USING BTREE,
  INDEX `idx_sll_email` (`email`) USING BTREE,
  INDEX `idx_sll_time` (`login_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COMMENT = '店铺端登录来源流水';
