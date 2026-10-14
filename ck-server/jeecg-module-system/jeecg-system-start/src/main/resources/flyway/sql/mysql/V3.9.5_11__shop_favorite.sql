-- =============================================================================
-- 商品收藏（我的收藏）· 数据模型
-- 版本：3.9.5.11
--
-- 设计要点：
--   1. 收藏是「用户 × 店铺 × 商品」的一条**关系**，笛卡尔积里没有 SKU 这一层：
--      用户在详情页点收藏时可能还没选规格，把 SKU 塞进唯一键会逼前端"先选规格才能收藏"。
--   2. 唯一键 uk_favorite_user_store_product **不含 del_flag**（与购物车同一取舍）：
--      取消收藏走软删除，旧行仍然占着这个键，再次收藏时必须先把它查出来**复活**，
--      否则 INSERT 直接撞唯一键报错。
--   3. 按店铺隔离（store_id）：多店铺下 A 店的收藏列表绝不能出现 B 店的商品。
--   4. 只存关系、不存商品快照：标题/封面/价格/库存/上下架状态一律在列表接口里实时读取。
--      存快照就会出现"收藏里还有一件早就下架的商品，价格还是三个月前的"。
--   5. 计数：ck_shop_product.favorite_count 是**冗余计数**，由收藏 Service
--      在同一个事务里增量维护（+1 / -1，且不小于 0），与关系表同生共死。
--
-- 全部写成可重复执行（IF NOT EXISTS）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `ck_shop_favorite` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `user_id` varchar(36) NOT NULL COMMENT '用户ID(sys_user.id)',
  `username` varchar(50) DEFAULT NULL COMMENT '用户账号(冗余，便于后台统计直接展示)',
  `store_id` varchar(36) NOT NULL COMMENT '店铺ID(ck_shop_store.id)，收藏按店铺隔离',
  `product_id` varchar(36) NOT NULL COMMENT '商品ID(ck_shop_product.id)',
  `org_code` varchar(64) DEFAULT NULL COMMENT '收藏时商品归属部门(冗余快照，便于按店铺/部门排查)',
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记: 0-已收藏, 1-已取消收藏',
  PRIMARY KEY (`id`),
  -- 不含 del_flag：取消后再收藏靠"复活旧行"完成（见脚本头 第 2 点）
  UNIQUE KEY `uk_favorite_user_store_product` (`user_id`,`store_id`,`product_id`),
  -- 前台"我的收藏"恒为「某用户 + 某店铺 + 未取消 + 按收藏时间倒序」，索引按这个顺序建
  KEY `idx_favorite_user_store_time` (`user_id`,`store_id`,`del_flag`,`create_time`),
  -- 按商品维度反查（"这件商品被谁收藏了"）：后台运营排查与将来的收藏统计都要用，
  -- 商品详情页的收藏数走 ck_shop_product.favorite_count 冗余计数，不查这张表
  KEY `idx_favorite_product` (`product_id`,`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品收藏(按用户+店铺隔离, 用户×商品唯一)';
