-- =============================================================================
-- 商品详情：结构化详情模块 + 规格参数（Key-Value）
-- 与 db/ck-shop-product-detail.sql 内容一致，可重复执行。
-- 契约：docs/商品详情-设计契约.md
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
