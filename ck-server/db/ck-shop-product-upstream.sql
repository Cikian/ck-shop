-- =============================================================================
-- 商品管理 · 上游来源 / 上游链接
-- 与 Flyway 脚本 V3.9.5_4__add_product_upstream.sql 内容一致，
-- 便于新环境一次性初始化或人工排查时使用。可重复执行。
-- =============================================================================

-- ---------- 1. 商品表加列 ----------
SET @add_upstream := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `ck_shop_product`
       ADD COLUMN `upstream_source` varchar(50) DEFAULT NULL COMMENT ''上游来源(字典 shop_upstream_source)'' AFTER `brand_id`,
       ADD COLUMN `upstream_url` varchar(500) DEFAULT NULL COMMENT ''上游链接'' AFTER `upstream_source`',
    'SELECT ''ck_shop_product.upstream_source 已存在，跳过'' AS skipped'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ck_shop_product'
    AND COLUMN_NAME = 'upstream_source'
);
PREPARE stmt_add_upstream FROM @add_upstream;
EXECUTE stmt_add_upstream;
DEALLOCATE PREPARE stmt_add_upstream;

-- ---------- 2. 字典 ----------
INSERT INTO `sys_dict` (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `type`, `tenant_id`)
VALUES ('9000000000000000100', '商品上游来源', 'shop_upstream_source', '商品的上游采购来源', 0, 'admin', NOW(), 'admin', NOW(), 0, 0)
ON DUPLICATE KEY UPDATE
  `dict_name` = VALUES(`dict_name`),
  `description` = VALUES(`description`),
  `del_flag` = 0,
  `update_time` = NOW();

SET @upstream_dict_id := (SELECT `id` FROM `sys_dict` WHERE `dict_code` = 'shop_upstream_source' LIMIT 1);

-- ---------- 3. 字典项（暂定：淘宝 / 京东 / 1688 / 闲鱼） ----------
DELETE FROM `sys_dict_item` WHERE `dict_id` = @upstream_dict_id;

INSERT INTO `sys_dict_item` (`id`, `dict_id`, `item_text`, `item_value`, `description`, `sort_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('9000000000000000111', @upstream_dict_id, '淘宝', 'taobao', NULL, 1, 1, 'admin', NOW(), 'admin', NOW()),
('9000000000000000112', @upstream_dict_id, '京东', 'jd',     NULL, 2, 1, 'admin', NOW(), 'admin', NOW()),
('9000000000000000113', @upstream_dict_id, '1688', '1688',   NULL, 3, 1, 'admin', NOW(), 'admin', NOW()),
('9000000000000000114', @upstream_dict_id, '闲鱼', 'xianyu', NULL, 4, 1, 'admin', NOW(), 'admin', NOW());
