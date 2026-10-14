-- =============================================================================
-- 购物车 + 加购统计 · 数据模型（手工执行版）
--
-- 说明：这份脚本与
--   jeecg-module-system/jeecg-system-start/src/main/resources/flyway/sql/mysql/V3.9.5_8__shop_cart.sql
-- 内容完全一致。Flyway 会在应用下次启动时自动执行迁移；本文件供不方便重启应用时
-- 直接在数据库里手工执行（可重复执行）。
--
-- 表结构说明见 Flyway 脚本内的注释。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `ck_shop_cart_item` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `user_id` varchar(36) NOT NULL COMMENT '用户ID(sys_user.id)',
  `username` varchar(50) DEFAULT NULL COMMENT '用户账号(冗余，便于后台统计直接展示)',
  `store_id` varchar(36) NOT NULL COMMENT '店铺ID(ck_shop_store.id)，购物车按店铺隔离',
  `product_id` varchar(36) NOT NULL COMMENT '商品ID(ck_shop_product.id)',
  `sku_id` varchar(36) NOT NULL DEFAULT '' COMMENT 'SKU组合ID(ck_shop_product_sku_combination.id)；无规格商品为空串',
  `quantity` int(11) NOT NULL DEFAULT 1 COMMENT '购买数量',
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记: 0-正常, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_user_store_sku` (`user_id`,`store_id`,`product_id`,`sku_id`),
  KEY `idx_cart_user_store` (`user_id`,`store_id`,`del_flag`),
  KEY `idx_cart_store_time` (`store_id`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车明细(按用户+店铺隔离, 主体为SKU)';

CREATE TABLE IF NOT EXISTS `ck_shop_cart_event` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `user_id` varchar(36) NOT NULL COMMENT '用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户账号',
  `store_id` varchar(36) DEFAULT NULL COMMENT '店铺ID',
  `product_id` varchar(36) NOT NULL COMMENT '商品ID',
  `sku_id` varchar(36) NOT NULL DEFAULT '' COMMENT 'SKU组合ID；无规格商品为空串',
  `quantity` int(11) NOT NULL DEFAULT 0 COMMENT '本次变动数量(取绝对值，恒为正)',
  `event_type` varchar(16) NOT NULL COMMENT 'ADD-首次加入 / INCR-同SKU累加 / UPDATE-改数量 / REMOVE-删除 / CLEAR-清空',
  `source` varchar(32) DEFAULT NULL COMMENT '来源: detail-商品详情 / list-商品列表 / cart-购物车页 / api-其它',
  `org_code` varchar(64) DEFAULT NULL COMMENT '加购时商品归属部门(冗余快照)',
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cart_event_store_time` (`store_id`,`create_time`),
  KEY `idx_cart_event_org_time` (`org_code`,`create_time`),
  KEY `idx_cart_event_product_time` (`product_id`,`create_time`),
  KEY `idx_cart_event_sku_time` (`sku_id`,`create_time`),
  KEY `idx_cart_event_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加购行为流水(只追加, 后台统计数据源)';
