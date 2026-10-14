-- =============================================================================
-- 商品收藏（我的收藏）· 数据模型（手工执行版）
--
-- 说明：这份脚本与
--   jeecg-module-system/jeecg-system-start/src/main/resources/flyway/sql/mysql/V3.9.5_11__shop_favorite.sql
-- 内容完全一致。Flyway 会在应用下次启动时自动执行迁移；本文件供不方便重启应用时
-- 直接在数据库里手工执行（可重复执行）。
--
-- 表结构说明见 Flyway 脚本内的注释。
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
