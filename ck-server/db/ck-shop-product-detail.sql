-- =============================================================================
-- 商品详情：结构化详情模块 + 规格参数（Key-Value）
--
-- 契约见 docs/商品详情-设计契约.md（本文与 Flyway 脚本 V3.9.5_13 内容一致）。
-- 全部使用 CREATE TABLE IF NOT EXISTS，可重复执行；不修改、不删除任何既有数据。
--
-- 为什么不复用 ck_shop_product_spec：
--   spec 是销售维度（决定 SKU 组合与价格），本节两张表是**展示维度**（客户只看不选）。
--
-- 为什么不加 del_flag：
--   模块/参数是内容型数据，不被订单引用、也没有唯一索引；物理删除即可，
--   逻辑删除会让长文本行无限堆积（该原则见 docs/商品管理-验证记录.md 第 17 节）。
-- =============================================================================

-- =============================================================================
-- 1. 商品详情模块（按 商品 + 语言 分行，同一语言内按 sort_order 升序展示）
-- =============================================================================
CREATE TABLE IF NOT EXISTS `ck_shop_product_detail_block` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `product_id` varchar(36) NOT NULL COMMENT '商品ID(ck_shop_product.id)',
  `language_code` varchar(10) NOT NULL COMMENT '语言代码: zh/en/fr...',
  `block_type` varchar(32) NOT NULL COMMENT '模块类型: IMAGE/IMAGE_GRID/IMAGE_TEXT/TEXT/RICH_TEXT/VIDEO/TITLE/DIVIDER',
  `content` longtext COMMENT '模块内容(JSON 字符串, 结构见契约 1.3)',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '同语言内展示顺序(升序)',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建日期',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新日期',
  PRIMARY KEY (`id`),
  KEY `idx_block_product_lang` (`product_id`,`language_code`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品详情模块';

-- =============================================================================
-- 2. 规格参数（Key-Value，按 商品 + 语言 分行）
-- =============================================================================
CREATE TABLE IF NOT EXISTS `ck_shop_product_param` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `product_id` varchar(36) NOT NULL COMMENT '商品ID(ck_shop_product.id)',
  `language_code` varchar(10) NOT NULL COMMENT '语言代码: zh/en/fr...',
  `param_name` varchar(100) NOT NULL COMMENT '参数名(如: 屏幕尺寸)',
  `param_value` varchar(500) NOT NULL COMMENT '参数值(如: 6.7 英寸)',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '展示顺序(升序)',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建日期',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新日期',
  PRIMARY KEY (`id`),
  KEY `idx_param_product_lang` (`product_id`,`language_code`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格参数(展示用)';

-- =============================================================================
-- 校验（人工执行时可读）：两表应各存在 1 条复合索引
-- =============================================================================
-- SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX
-- FROM information_schema.STATISTICS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN ('ck_shop_product_detail_block', 'ck_shop_product_param')
-- ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;
