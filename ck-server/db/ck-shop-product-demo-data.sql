-- =============================================================================
-- 商品规格/SKU 演示数据：用例来自需求原文
--   手机三个规格：颜色（白色/红色/黑色）× 内存（8G/16G）× 存储（256G/512G/1T）
--   完整笛卡尔积 18 种，故意缺少 "白色/8G/1T" 这一种，
--   用于验证店铺端「选择白色 + 8G 后，存储里的 1T 应置灰不可选」。
-- 说明：MySQL 5.7 兼容（未使用窗口函数 / CTE）；可重复执行。
-- =============================================================================

DELETE FROM `ck_shop_product_sku_combination_value` WHERE `product_id` = '9000000000000000001';
DELETE FROM `ck_shop_product_sku_combination` WHERE `product_id` = '9000000000000000001';
DELETE FROM `ck_shop_product_spec_value` WHERE `product_id` = '9000000000000000001';
DELETE FROM `ck_shop_product_spec` WHERE `product_id` = '9000000000000000001';
DELETE FROM `ck_shop_product_translation` WHERE `product_id` = '9000000000000000001';
DELETE FROM `ck_shop_product` WHERE `id` = '9000000000000000001';

-- 商品主表（价格区间与总库存与下面 SKU 数据保持一致）
-- upstream_source 取值来自字典 shop_upstream_source（淘宝/京东/1688/闲鱼）
INSERT INTO `ck_shop_product`
(`id`, `spu_code`, `price`, `min_price`, `max_price`, `stock`, `unit`, `status`, `sort_order`, `is_recommend`,
 `category_id`, `brand_id`, `upstream_source`, `upstream_url`, `main_image`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
VALUES
('9000000000000000001', 'SPU-PHONE-DEMO', 2999.00, 2999.00, 4299.00, 351, '台', 'Y', 1, 'Y',
 '2083975253329358850', NULL, '1688', 'https://detail.1688.com/offer/demo.html', NULL, 'admin', NOW(), 'admin', NOW(), 0);

-- 多语言
INSERT INTO `ck_shop_product_translation`
(`id`, `product_id`, `language_code`, `title`, `sub_title`, `description`, `cover_url`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
('9000000000000000101', '9000000000000000001', 'zh', '示例手机 X1', '三规格可自由组合', '<p>用于验证规格组合的示例商品</p>', NULL, 'admin', NOW(), 'admin', NOW()),
('9000000000000000102', '9000000000000000001', 'en', 'Demo Phone X1', 'Three specs, free combination', '<p>Demo product for spec combination</p>', NULL, 'admin', NOW(), 'admin', NOW());

-- 规格定义
INSERT INTO `ck_shop_product_spec`
(`id`, `product_id`, `name`, `preset_code`, `sort_order`, `is_required`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
('9000000000000000201', '9000000000000000001', '颜色', 'color', 0, 'Y', 0, 'admin', NOW(), 'admin', NOW()),
('9000000000000000202', '9000000000000000001', '内存', 'memory', 1, 'Y', 0, 'admin', NOW(), 'admin', NOW()),
('9000000000000000203', '9000000000000000001', '存储', 'storage', 2, 'Y', 0, 'admin', NOW(), 'admin', NOW());

-- 规格可选取值（规格值不配图，图片配在具体 SKU 上）
INSERT INTO `ck_shop_product_spec_value`
(`id`, `spec_id`, `product_id`, `value`, `sort_order`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
('9000000000000000301', '9000000000000000201', '9000000000000000001', '白色', 0, 0, 'admin', NOW(), 'admin', NOW()),
('9000000000000000302', '9000000000000000201', '9000000000000000001', '红色', 1, 0, 'admin', NOW(), 'admin', NOW()),
('9000000000000000303', '9000000000000000201', '9000000000000000001', '黑色', 2, 0, 'admin', NOW(), 'admin', NOW()),
('9000000000000000311', '9000000000000000202', '9000000000000000001', '8G',  0, 0, 'admin', NOW(), 'admin', NOW()),
('9000000000000000312', '9000000000000000202', '9000000000000000001', '16G', 1, 0, 'admin', NOW(), 'admin', NOW()),
('9000000000000000321', '9000000000000000203', '9000000000000000001', '256G', 0, 0, 'admin', NOW(), 'admin', NOW()),
('9000000000000000322', '9000000000000000203', '9000000000000000001', '512G', 1, 0, 'admin', NOW(), 'admin', NOW()),
('9000000000000000323', '9000000000000000203', '9000000000000000001', '1T',  2, 0, 'admin', NOW(), 'admin', NOW());

-- SKU 组合：18 种里去掉「白色/8G/1T」
INSERT INTO `ck_shop_product_sku_combination`
(`id`, `product_id`, `sku_code`, `combination_key`, `combination_name`, `price`, `stock`, `saleable`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
('9000000000000000401','9000000000000000001','SKU-X1-01','9000000000000000301-9000000000000000311-9000000000000000321','白色/8G/256G',2999.00,12,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000402','9000000000000000001','SKU-X1-02','9000000000000000301-9000000000000000311-9000000000000000322','白色/8G/512G',2999.00,15,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000404','9000000000000000001','SKU-X1-04','9000000000000000301-9000000000000000312-9000000000000000321','白色/16G/256G',3599.00,13,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000405','9000000000000000001','SKU-X1-05','9000000000000000301-9000000000000000312-9000000000000000322','白色/16G/512G',3599.00,16,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000406','9000000000000000001','SKU-X1-06','9000000000000000301-9000000000000000312-9000000000000000323','白色/16G/1T',4299.00,19,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000407','9000000000000000001','SKU-X1-07','9000000000000000302-9000000000000000311-9000000000000000321','红色/8G/256G',2999.00,17,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000408','9000000000000000001','SKU-X1-08','9000000000000000302-9000000000000000311-9000000000000000322','红色/8G/512G',2999.00,20,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000409','9000000000000000001','SKU-X1-09','9000000000000000302-9000000000000000311-9000000000000000323','红色/8G/1T',4299.00,23,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000410','9000000000000000001','SKU-X1-10','9000000000000000302-9000000000000000312-9000000000000000321','红色/16G/256G',3599.00,18,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000411','9000000000000000001','SKU-X1-11','9000000000000000302-9000000000000000312-9000000000000000322','红色/16G/512G',3599.00,21,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000412','9000000000000000001','SKU-X1-12','9000000000000000302-9000000000000000312-9000000000000000323','红色/16G/1T',4299.00,24,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000413','9000000000000000001','SKU-X1-13','9000000000000000303-9000000000000000311-9000000000000000321','黑色/8G/256G',2999.00,22,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000414','9000000000000000001','SKU-X1-14','9000000000000000303-9000000000000000311-9000000000000000322','黑色/8G/512G',2999.00,25,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000415','9000000000000000001','SKU-X1-15','9000000000000000303-9000000000000000311-9000000000000000323','黑色/8G/1T',4299.00,28,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000416','9000000000000000001','SKU-X1-16','9000000000000000303-9000000000000000312-9000000000000000321','黑色/16G/256G',3599.00,23,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000417','9000000000000000001','SKU-X1-17','9000000000000000303-9000000000000000312-9000000000000000322','黑色/16G/512G',3599.00,26,'Y',0,'admin',NOW(),'admin',NOW()),
('9000000000000000418','9000000000000000001','SKU-X1-18','9000000000000000303-9000000000000000312-9000000000000000323','黑色/16G/1T',4299.00,29,'Y',0,'admin',NOW(),'admin',NOW());

-- 组合 → 规格值 明细（用 SUBSTRING_INDEX 拆组合键，MySQL 5.7 可用）
INSERT INTO `ck_shop_product_sku_combination_value`
(`id`, `combination_id`, `product_id`, `spec_id`, `spec_value_id`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT CONCAT('90000000000000005', RIGHT(c.id, 2), t.pos),
       c.id,
       c.product_id,
       CASE t.pos
         WHEN 1 THEN '9000000000000000201'
         WHEN 2 THEN '9000000000000000202'
         ELSE '9000000000000000203'
       END,
       CASE t.pos
         WHEN 1 THEN SUBSTRING_INDEX(SUBSTRING_INDEX(c.combination_key, '-', 1), '-', -1)
         WHEN 2 THEN SUBSTRING_INDEX(SUBSTRING_INDEX(c.combination_key, '-', 2), '-', -1)
         ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(c.combination_key, '-', 3), '-', -1)
       END,
       0, 'admin', NOW(), 'admin', NOW()
FROM `ck_shop_product_sku_combination` c
CROSS JOIN (SELECT 1 AS pos UNION ALL SELECT 2 UNION ALL SELECT 3) t
WHERE c.product_id = '9000000000000000001';

-- 预设规格字典（供商品编辑页「从预设库导入」使用）
DELETE FROM `ck_shop_options` WHERE `option_code` IN ('color', 'memory', 'storage');
INSERT INTO `ck_shop_options` (`id`, `option_code`, `option_value`, `option_name`, `description`, `sort_order`, `is_show`, `del_flag`)
VALUES
('9000000000000000601', 'color',   '白色,红色,黑色',        '颜色', '手机颜色', 1, 'Y', 0),
('9000000000000000602', 'memory',  '8G,16G',                '内存', '运行内存', 2, 'Y', 0),
('9000000000000000603', 'storage', '256G,512G,1T',          '存储', '机身存储', 3, 'Y', 0);
