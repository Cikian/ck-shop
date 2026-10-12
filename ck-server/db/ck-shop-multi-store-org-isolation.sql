-- =============================================================================
-- 多店铺改造 · 第二步：把数据隔离维度从「自建店铺关系表」换成 JeecgBoot 部门(sys_org_code)
-- 版本：3.9.5.6
--
-- 已确认的决策：
--   1. 删除「品牌」概念（表、外键列、菜单与按钮权限）——品牌无业务意义。
--   2. 一商品只在一个店铺上架：删除 ck_shop_product_store（不再有"多渠道/销售渠道"）。
--   3. 数据隔离维度 = JeecgBoot 部门：归属列统一为 sys_org_code；店铺 ↔ 部门 1:1。
--   4. 分类按店独立：ck_shop_category.store_id → sys_org_code。
--   5. spu_code 唯一性按部门：uk(sys_org_code, spu_code)，各店可重复
--      （不影响前台路由：店铺身份来自域名 / X-Store-Code 请求头）。
--   6. 运营只能管本部门数据，可见域 sys_org_code LIKE '当前部门org_code%'（前缀式编码天然支持"本部门及以下"）；
--      新增权限码 shop-store:manage-all 表示"不限部门（总部/管理员）"。
--
-- 全部语句写成可重复执行（幂等），dev 环境手工执行过也不会报错。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ① 店铺：depart_id（绑定部门）+ 索引；并把 default → 数码部(A06A01)、outdoor → 户外部(A06A02) 绑好
--    必须先做这一步：后面商品存量数据要靠它推导归属部门。
-- -----------------------------------------------------------------------------
SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE `ck_shop_store` ADD COLUMN `depart_id` varchar(32) DEFAULT NULL COMMENT ''绑定部门(sys_depart.id)''',
    'SELECT ''ck_shop_store.depart_id 已存在，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_store' AND COLUMN_NAME='depart_id'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE `ck_shop_store` ADD KEY `idx_ck_shop_store_depart` (`depart_id`)',
    'SELECT ''店铺部门索引已存在，跳过'' AS skipped')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_store' AND INDEX_NAME='idx_ck_shop_store_depart'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE `ck_shop_store` SET `depart_id` = (SELECT `id` FROM `sys_depart` WHERE `del_flag`=0 AND `org_code`='A06A01' LIMIT 1)
WHERE `code`='default' AND (`depart_id` IS NULL OR `depart_id`='');
UPDATE `ck_shop_store` SET `depart_id` = (SELECT `id` FROM `sys_depart` WHERE `del_flag`=0 AND `org_code`='A06A02' LIMIT 1)
WHERE `code`='outdoor' AND (`depart_id` IS NULL OR `depart_id`='');

-- -----------------------------------------------------------------------------
-- ② 商品：sys_org_code 归属列 + 组合索引
-- -----------------------------------------------------------------------------
SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE `ck_shop_product` ADD COLUMN `sys_org_code` varchar(64) DEFAULT NULL COMMENT ''归属部门编码(数据隔离维度)''',
    'SELECT ''ck_shop_product.sys_org_code 已存在，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_product' AND COLUMN_NAME='sys_org_code'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE `ck_shop_product` ADD KEY `idx_ck_shop_product_org` (`sys_org_code`,`status`,`category_id`,`sort_order`)',
    'SELECT ''商品部门索引已存在，跳过'' AS skipped')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_product' AND INDEX_NAME='idx_ck_shop_product_org'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- -----------------------------------------------------------------------------
-- ③ 存量商品归属：默认店铺绑定的部门。
--    注意顺序：先把可推导的填上，再处理"确实推导不出来"的行——
--    它们属于历史脏数据，保留 NULL 会在唯一键与可见域上产生歧义，
--    因此这里统一归入默认店铺部门，而不是留着 NULL。
-- -----------------------------------------------------------------------------
UPDATE `ck_shop_product` p
  JOIN `ck_shop_store` st ON st.`code`='default'
  JOIN `sys_depart` d ON d.`id`=st.`depart_id`
   SET p.`sys_org_code` = d.`org_code`
 WHERE (p.`sys_org_code` IS NULL OR p.`sys_org_code`='')
   AND d.`org_code` IS NOT NULL;

-- -----------------------------------------------------------------------------
-- ④ spu_code 唯一性：由全局唯一改为「按部门唯一」uk(sys_org_code, spu_code)
--    （旧脚本把唯一列改成了 sys_org_code+spu_code，这里保证老库也能收敛到目标形态）
-- -----------------------------------------------------------------------------
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_product' AND INDEX_NAME='uk_spu_code') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_product'
        AND INDEX_NAME='uk_spu_code' AND SEQ_IN_INDEX=2 AND COLUMN_NAME='spu_code') = 0,
    'ALTER TABLE `ck_shop_product` DROP INDEX `uk_spu_code`',
    'SELECT ''uk_spu_code 不存在或已按部门重建，跳过'' AS skipped')
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE `ck_shop_product` ADD UNIQUE KEY `uk_spu_code` (`sys_org_code`,`spu_code`)',
    'SELECT ''uk_spu_code 已存在，跳过'' AS skipped')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_product' AND INDEX_NAME='uk_spu_code'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- -----------------------------------------------------------------------------
-- ⑤ 分类：加 sys_org_code + 索引；存量归属由 store_id 对应店铺的部门推导
-- -----------------------------------------------------------------------------
SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE `ck_shop_category` ADD COLUMN `sys_org_code` varchar(64) DEFAULT NULL COMMENT ''归属部门编码(数据隔离维度)''',
    'SELECT ''ck_shop_category.sys_org_code 已存在，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_category' AND COLUMN_NAME='sys_org_code'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE `ck_shop_category` ADD KEY `idx_ck_shop_category_org_show` (`sys_org_code`,`is_show`,`sort_order`), ADD KEY `idx_ck_shop_category_org_parent` (`sys_org_code`,`parent_id`)',
    'SELECT ''分类部门索引已存在，跳过'' AS skipped')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_category' AND INDEX_NAME='idx_ck_shop_category_org_show'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 存量分类归属：store_id → 店铺 → 部门；仍为空（全局共享节点/无归属）的统一归入默认店铺部门
SET @sql := (
  SELECT IF(COUNT(*) > 0,
    'UPDATE `ck_shop_category` c JOIN `ck_shop_store` st ON st.`id`=c.`store_id` JOIN `sys_depart` d ON d.`id`=st.`depart_id` SET c.`sys_org_code`=d.`org_code` WHERE (c.`sys_org_code` IS NULL OR c.`sys_org_code`='''') AND d.`org_code` IS NOT NULL',
    'SELECT ''分类 store_id 列不存在，跳过推导'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_category' AND COLUMN_NAME='store_id'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE `ck_shop_category` c
  JOIN `ck_shop_store` st ON st.`code`='default'
  JOIN `sys_depart` d ON d.`id`=st.`depart_id`
   SET c.`sys_org_code` = d.`org_code`
 WHERE (c.`sys_org_code` IS NULL OR c.`sys_org_code`='') AND d.`org_code` IS NOT NULL;

-- 删掉按店铺的旧索引与 store_id 列（分类的归属维度已彻底换成部门）
SET @sql := (
  SELECT IF(COUNT(*)>0, 'ALTER TABLE `ck_shop_category` DROP INDEX `idx_ck_shop_category_store_show`',
    'SELECT ''旧分类索引不存在，跳过'' AS skipped')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_category' AND INDEX_NAME='idx_ck_shop_category_store_show'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)>0, 'ALTER TABLE `ck_shop_category` DROP INDEX `idx_ck_shop_category_store_parent`',
    'SELECT ''旧分类索引不存在，跳过'' AS skipped')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_category' AND INDEX_NAME='idx_ck_shop_category_store_parent'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)>0, 'ALTER TABLE `ck_shop_category` DROP COLUMN `store_id`',
    'SELECT ''分类 store_id 已删除，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_category' AND COLUMN_NAME='store_id'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- -----------------------------------------------------------------------------
-- ⑥ 删除「品牌」：外键列 + 索引 + 两张表
-- -----------------------------------------------------------------------------
SET @sql := (
  SELECT IF(COUNT(*)>0, 'ALTER TABLE `ck_shop_product` DROP INDEX `idx_ck_shop_product_brand`',
    'SELECT ''品牌索引不存在，跳过'' AS skipped')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_product' AND INDEX_NAME='idx_ck_shop_product_brand'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)>0, 'ALTER TABLE `ck_shop_product` DROP COLUMN `brand_id`',
    'SELECT ''商品 brand_id 已删除，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ck_shop_product' AND COLUMN_NAME='brand_id'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

DROP TABLE IF EXISTS `ck_shop_brand_translation`;
DROP TABLE IF EXISTS `ck_shop_brand`;

-- -----------------------------------------------------------------------------
-- ⑦ 删除「商品-店铺上架关系」：一商品只属于一个店铺，不再需要渠道表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ck_shop_product_store`;

-- -----------------------------------------------------------------------------
-- ⑧ 菜单与权限
--    删：品牌管理菜单(…003) + 6 个品牌按钮(…021~026) + 对应角色授权
--    增：shop-store:manage-all(…047) = 不限部门（总部/管理员），只授给 admin 角色
-- -----------------------------------------------------------------------------
DELETE FROM `sys_role_permission`
 WHERE `permission_id` IN ('2090000000000000003',
                            '2090000000000000021','2090000000000000022','2090000000000000023',
                            '2090000000000000024','2090000000000000025','2090000000000000026');

DELETE FROM `sys_permission`
 WHERE `id` IN ('2090000000000000021','2090000000000000022','2090000000000000023',
                '2090000000000000024','2090000000000000025','2090000000000000026',
                '2090000000000000003');

INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT '2090000000000000047', '2090000000000000005', '不限部门(总部)', NULL, NULL, 0, NULL, NULL, 2,
       'shop-store:manage-all', '1', NULL, 0, NULL, 1, 0, 0, 0,
       '拥有该权限即可跨部门查看与管理全部店铺数据（总部/管理员）',
       'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `id` = '2090000000000000047');

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('2', SUBSTRING(MD5(CONCAT(r.`id`, p.`id`)), 1, 31)),
       r.`id`, p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
JOIN `sys_role` r ON r.`role_code` = 'admin'
WHERE p.`id` = '2090000000000000047'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_permission` rp
      WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
