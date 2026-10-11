-- =============================================================================
-- 多店铺（多前台站点）改造 · 第一步：数据模型
-- 版本：3.9.5.5
--
-- 设计要点（与 docs/多店铺设计思路 一致）：
--   1. 商品主体（SPU/规格/SKU/图集/多语言）保持**全局一份**，不加 store_id；
--      "属于哪个店"是**渠道属性**，落在 ck_shop_product_store 上。
--   2. ck_shop_product_store 同时承载两档需求：
--      - 第一档：本店是否上架（status）
--      - 第二档：本店售价（price_override，空则回退商品基准价）
--      另外 category_id 表示本店分类（空则回退商品主分类）——因为分类是"按店独立"的。
--   3. 分类按店独立：ck_shop_category.store_id（**空 = 全局共享节点**，非空 = 该店专属）。
--      品牌/标签仍全局共享，前台按"本店有商品"自动推导，不做映射表。
--   4. spu_code 唯一性收紧为**全局唯一**：它将成为前台路由标识，
--      同一个编码必须永远只对应一个商品（含已删除商品，禁止复用），
--      否则已分享出去的旧链接会指向另一个商品。
--   5. 所有店铺端索引一律以 store_id 打头 —— 既保证效率，也让"漏加店铺条件"的查询容易暴露。
--
-- 全部写成可重复执行。
-- =============================================================================

-- ① 店铺表
CREATE TABLE IF NOT EXISTS `ck_shop_store` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `code` varchar(50) NOT NULL COMMENT '店铺编码(接口/配置用, 唯一)',
  `name` varchar(100) NOT NULL COMMENT '店铺名称',
  `domain` varchar(150) DEFAULT NULL COMMENT '前台域名(用于 Host 兜底解析, 可空)',
  `logo_url` varchar(500) DEFAULT NULL COMMENT '店铺Logo',
  `theme_config` text COMMENT '主题配置JSON',
  `description` varchar(255) DEFAULT NULL COMMENT '简介',
  `sort_order` int(11) DEFAULT 0 COMMENT '排序',
  `status` varchar(2) DEFAULT 'Y' COMMENT '状态: N-停用, Y-启用',
  `del_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记: 0-正常, 1-已删除',
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ck_shop_store_code` (`code`),
  KEY `idx_ck_shop_store_domain` (`domain`),
  KEY `idx_ck_shop_store_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺(前台站点)';

-- ② 商品-店铺上架关系（渠道属性）
CREATE TABLE IF NOT EXISTS `ck_shop_product_store` (
  `id` varchar(36) NOT NULL,
  `product_id` varchar(36) NOT NULL COMMENT '商品ID',
  `store_id` varchar(36) NOT NULL COMMENT '店铺ID',
  `category_id` varchar(36) DEFAULT NULL COMMENT '本店分类(空则回退商品主分类)',
  `price_override` decimal(10,2) DEFAULT NULL COMMENT '本店售价(空则回退商品基准价)',
  `status` varchar(2) DEFAULT 'Y' COMMENT '本店是否上架: N-下架, Y-上架',
  `sort_order` int(11) DEFAULT 0 COMMENT '本店排序',
  `is_recommend` varchar(2) DEFAULT 'N' COMMENT '本店是否推荐',
  `del_flag` tinyint(1) NOT NULL DEFAULT 0,
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  -- 注意：唯一键不含 del_flag，软删除的行仍占用该键。
  -- 因此"下架某店后再次上架"必须走 INSERT ... ON DUPLICATE KEY UPDATE 复活旧行，
  -- 而不是先删再插（那会撞唯一键）。
  UNIQUE KEY `uk_ck_shop_ps_product_store` (`product_id`,`store_id`),
  KEY `idx_ck_shop_ps_store_list` (`store_id`,`status`,`category_id`,`sort_order`),
  KEY `idx_ck_shop_ps_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品-店铺上架关系';

-- ③ 分类加 store_id（空=全局共享）与 del_flag
SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE `ck_shop_category` ADD COLUMN `store_id` varchar(36) DEFAULT NULL COMMENT ''所属店铺(空=全局共享)'', ADD COLUMN `del_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''删除标记''',
    'SELECT ''ck_shop_category 已改造，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_category' AND COLUMN_NAME='store_id'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ④ 分类索引：一律以 store_id 打头
SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE `ck_shop_category` ADD KEY `idx_ck_shop_category_store_show` (`store_id`,`is_show`,`sort_order`), ADD KEY `idx_ck_shop_category_store_parent` (`store_id`,`parent_id`)',
    'SELECT ''分类索引已存在，跳过'' AS skipped')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_category' AND INDEX_NAME='idx_ck_shop_category_store_show'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ⑤ spu_code：由 (spu_code,status) 改为全局唯一(spu_code)
SET @sql := (
  SELECT IF(COUNT(*)>0,
    'ALTER TABLE `ck_shop_product` DROP INDEX `uk_spu_code`',
    'SELECT ''uk_spu_code 不存在，跳过'' AS skipped')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_product' AND INDEX_NAME='uk_spu_code'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE `ck_shop_product` ADD UNIQUE KEY `uk_spu_code` (`spu_code`)',
    'SELECT ''uk_spu_code 已重建，跳过'' AS skipped')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_product' AND INDEX_NAME='uk_spu_code'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ⑥ 默认店铺 + 存量商品迁移（否则店铺端按店过滤后会什么都看不到）
INSERT INTO `ck_shop_store` (`id`,`code`,`name`,`domain`,`description`,`sort_order`,`status`,`del_flag`,`create_by`,`create_time`,`update_by`,`update_time`)
VALUES ('9000000000000000200','default','默认店铺',NULL,'系统初始化店铺',0,'Y',0,'admin',NOW(),'admin',NOW())
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `del_flag`=0, `update_time`=NOW();

INSERT INTO `ck_shop_product_store`
(`id`,`product_id`,`store_id`,`category_id`,`price_override`,`status`,`sort_order`,`is_recommend`,`del_flag`,`create_by`,`create_time`,`update_by`,`update_time`)
SELECT REPLACE(UUID(),'-',''), p.`id`, '9000000000000000200', p.`category_id`, NULL, p.`status`, p.`sort_order`, p.`is_recommend`, 0, 'admin', NOW(), 'admin', NOW()
FROM `ck_shop_product` p
WHERE p.`del_flag` = 0
ON DUPLICATE KEY UPDATE `del_flag`=0, `status`=VALUES(`status`), `update_time`=NOW();
