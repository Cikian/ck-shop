-- =============================================================================
-- 商品评价：新增「评价图片」列
-- 版本：3.9.5.21（2026-10-23）
--
-- 背景：店铺端把评价区改成「正文预览 3 条 + 左侧抽屉看全部」后，评价需要能带图
--       （参考电商详情页的买家秀）。原表只有纯文本 content，图片无处安放。
--
-- 设计取舍：
--   1) 存**一列 JSON 数组字符串**（`["https://…","https://…"]`），不另开子表：
--      评价图片是"跟着一条评价走的只读列表"，没有单独查询/统计需求，
--      子表只会多一次 JOIN 和一套增删改逻辑；
--   2) 列宽 1500：一条评价最多 9 张图（详见服务端 MAX_REVIEW_IMAGES），
--      CDN 链接按 150 字符预算，9 × 150 = 1350，留出余量；
--   3) 服务端读取时做**白名单校验**（只认 http(s) 与站内绝对路径，最多 9 张），
--      这条列将来接上传接口时不会被塞进 javascript: 之类的地址；
--   4) 允许为 NULL —— 绝大多数评价没有图，NULL 表示"不渲染图片区"。
--
-- MySQL 5.7 没有 ADD COLUMN IF NOT EXISTS，这里用 information_schema 判断后动态执行，
-- 保证脚本可重复执行（与 V3.9.5_15 的"全部幂等"约定一致）。
-- =============================================================================

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.`COLUMNS`
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ck_shop_product_review'
    AND COLUMN_NAME = 'images'
);

SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `ck_shop_product_review` ADD COLUMN `images` varchar(1500) DEFAULT NULL COMMENT ''评价图片(JSON 数组字符串；NULL=无图)'' AFTER `content`',
  'DO 0'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
