-- =============================================================================
-- 购物车 + 加购统计 · 数据模型
-- 版本：3.9.5.8
--
-- 设计要点：
--   1. 购物车**主体是 SKU**，不是商品：同一商品的不同规格各占一行。
--      无规格商品用 sku_id = ''（空串，不是 NULL）表示 —— 这是刻意为之：
--      MySQL 唯一索引里 NULL 彼此不相等，用 NULL 会让"同一商品重复加购"插入多行。
--   2. 购物车**按店铺隔离**（store_id）：多店铺架构下 A 店和 B 店的购物车互不可见，
--      否则前台会拿到别店的价格与库存。所有查询一律带 user_id + store_id。
--   3. 购物车**不存价格/库存快照**：价格与库存必须实时反映，存快照只会让用户
--      在结算时发现"价格变了"。下架/无货由列表接口实时判定并置灰。
--   4. 加购行为**独立事件表** ck_shop_cart_event：流水只追加不修改，
--      后台可按时间/商品/SKU/用户任意维度汇总，也不会因为购物车被清空而丢数据。
--
-- 全部写成可重复执行。
-- =============================================================================

-- ① 购物车明细
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
  -- 唯一键含 del_flag 会让"删除后重新加购"撞键（旧行 del_flag=1 仍占位），
  -- 因此这里**不含 del_flag**：删除走软删除，重新加购时把旧行复活（Service 里 upsert）。
  UNIQUE KEY `uk_cart_user_store_sku` (`user_id`,`store_id`,`product_id`,`sku_id`),
  KEY `idx_cart_user_store` (`user_id`,`store_id`,`del_flag`),
  KEY `idx_cart_store_time` (`store_id`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车明细(按用户+店铺隔离, 主体为SKU)';

-- ② 加购行为流水（后台统计的数据源）
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
  `org_code` varchar(64) DEFAULT NULL COMMENT '加购时商品归属部门(冗余快照，后台按店铺/部门过滤不用回表)',
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  -- 后台统计一律是"某店铺 + 某时间段"，因此索引以 store_id + create_time 打头
  KEY `idx_cart_event_store_time` (`store_id`,`create_time`),
  KEY `idx_cart_event_org_time` (`org_code`,`create_time`),
  KEY `idx_cart_event_product_time` (`product_id`,`create_time`),
  KEY `idx_cart_event_sku_time` (`sku_id`,`create_time`),
  KEY `idx_cart_event_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加购行为流水(只追加, 后台统计数据源)';
