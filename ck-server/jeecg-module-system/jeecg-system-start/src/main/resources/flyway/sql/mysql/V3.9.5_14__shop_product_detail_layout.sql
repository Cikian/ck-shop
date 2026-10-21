-- =============================================================================
-- 商品详情：模块四方向间距（长图切割后无缝拼接）
-- 与 db/ck-shop-product-detail-layout.sql 内容一致，可重复执行。
-- 契约：docs/商品详情-设计契约.md 第 1.4 节
--
-- 只加列、不动既有行；默认 0（模块之间默认没有任何留白）。
-- =============================================================================

SET @add_margin_top := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ck_shop_product_detail_block` ADD COLUMN `margin_top` int(11) NOT NULL DEFAULT 0 COMMENT ''上间距(px, 0-500)''',
    'SELECT ''ck_shop_product_detail_block.margin_top 已存在，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ck_shop_product_detail_block' AND COLUMN_NAME = 'margin_top'
);
PREPARE stmt_margin_top FROM @add_margin_top;
EXECUTE stmt_margin_top;
DEALLOCATE PREPARE stmt_margin_top;

SET @add_margin_right := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ck_shop_product_detail_block` ADD COLUMN `margin_right` int(11) NOT NULL DEFAULT 0 COMMENT ''右间距(px, 0-500)''',
    'SELECT ''ck_shop_product_detail_block.margin_right 已存在，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ck_shop_product_detail_block' AND COLUMN_NAME = 'margin_right'
);
PREPARE stmt_margin_right FROM @add_margin_right;
EXECUTE stmt_margin_right;
DEALLOCATE PREPARE stmt_margin_right;

SET @add_margin_bottom := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ck_shop_product_detail_block` ADD COLUMN `margin_bottom` int(11) NOT NULL DEFAULT 0 COMMENT ''下间距(px, 0-500)''',
    'SELECT ''ck_shop_product_detail_block.margin_bottom 已存在，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ck_shop_product_detail_block' AND COLUMN_NAME = 'margin_bottom'
);
PREPARE stmt_margin_bottom FROM @add_margin_bottom;
EXECUTE stmt_margin_bottom;
DEALLOCATE PREPARE stmt_margin_bottom;

SET @add_margin_left := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ck_shop_product_detail_block` ADD COLUMN `margin_left` int(11) NOT NULL DEFAULT 0 COMMENT ''左间距(px, 0-500)''',
    'SELECT ''ck_shop_product_detail_block.margin_left 已存在，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ck_shop_product_detail_block' AND COLUMN_NAME = 'margin_left'
);
PREPARE stmt_margin_left FROM @add_margin_left;
EXECUTE stmt_margin_left;
DEALLOCATE PREPARE stmt_margin_left;

-- 校验：应看到 margin_top / margin_right / margin_bottom / margin_left 四列
-- SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, COLUMN_COMMENT
-- FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ck_shop_product_detail_block'
--   AND COLUMN_NAME LIKE 'margin_%'
-- ORDER BY ORDINAL_POSITION;
