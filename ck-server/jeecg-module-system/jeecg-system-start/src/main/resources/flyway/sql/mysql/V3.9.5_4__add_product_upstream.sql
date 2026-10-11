-- =============================================================================
-- 商品增加「上游来源」与「上游链接」
-- 版本：3.9.5.4
--
-- 1. ck_shop_product 增加两列：
--    - upstream_source  上游来源，存字典值，字典 code = shop_upstream_source
--    - upstream_url     上游链接，详情页可直接点击跳转
-- 2. 建字典 shop_upstream_source，暂定四个选项：淘宝 / 京东 / 1688 / 闲鱼
--
-- 全部写成可重复执行：列用 information_schema 判断后动态 ALTER；
-- 字典用 ON DUPLICATE KEY（dict_code 有唯一索引）；
-- 字典项先按 dict_id 清空再插入，避免重复运行产生重复项。
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

-- 兼容"字典已存在但主键不是上面那个固定 id"的情况：统一按 dict_code 反查真实 id
SET @upstream_dict_id := (SELECT `id` FROM `sys_dict` WHERE `dict_code` = 'shop_upstream_source' LIMIT 1);

-- ---------- 3. 字典项 ----------
DELETE FROM `sys_dict_item` WHERE `dict_id` = @upstream_dict_id;

INSERT INTO `sys_dict_item` (`id`, `dict_id`, `item_text`, `item_value`, `description`, `sort_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('9000000000000000111', @upstream_dict_id, '淘宝', 'taobao', NULL, 1, 1, 'admin', NOW(), 'admin', NOW()),
('9000000000000000112', @upstream_dict_id, '京东', 'jd',     NULL, 2, 1, 'admin', NOW(), 'admin', NOW()),
('9000000000000000113', @upstream_dict_id, '1688', '1688',   NULL, 3, 1, 'admin', NOW(), 'admin', NOW()),
('9000000000000000114', @upstream_dict_id, '闲鱼', 'xianyu', NULL, 4, 1, 'admin', NOW(), 'admin', NOW());
