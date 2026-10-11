-- =============================================================================
-- 商品管理（商品 / 品牌 / 标签）菜单与按钮权限
-- 归属现有「商城」菜单（parent_id = 2079249150001979393，url = /shop）
-- 说明：
--   1. 本脚本已在开发库 ck-shop 中通过 DBX MCP 执行完毕，此处作为版本记录。
--   2. 权限 ID 使用 20900000000000000xx 号段，避免与雪花 ID 冲突。
--   3. 幂等：使用 NOT EXISTS 判断，可重复执行。
--   4. 同时给 admin 角色授予全部新权限，否则菜单不会出现（BACK 权限模式）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 菜单
-- -----------------------------------------------------------------------------
-- 自愈：早期版本的脚本把「商品管理」的 url 写成了 NULL，会导致后台路由构建崩溃
-- （joinParentPath 对 null 调 startsWith），这里对已存在的行做一次修正。
UPDATE `sys_permission`
SET `url` = '/shop/product-manage',
    `redirect` = '/shop/product',
    `update_by` = 'admin',
    `update_time` = NOW()
WHERE `id` = '2090000000000000001'
  AND (`url` IS NULL OR `url` = '');

-- 商品管理（目录）
-- 注意：目录型菜单的 url 必须非空。前端 menuHelper.joinParentPath 会对 menu.path
--       调用 startsWith，url 为 NULL 会让后端路由转菜单直接抛
--       "Cannot read properties of null (reading 'startsWith')"，整个后台卡在加载态。
--       redirect 指向第一个子菜单，便于点击父级时直达商品列表。
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT '2090000000000000001', '2079249150001979393', '商品管理', '/shop/product-manage', 'layouts/RouteView', 1, NULL, '/shop/product', 0,
       NULL, '0', 3.00, 0, 'ant-design:shopping-outlined', 0, 0, 0, 0,
       NULL, 'admin', NOW(), NULL, NULL, 0, 0, NULL, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `id` = '2090000000000000001');

-- 商品列表
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT '2090000000000000002', '2090000000000000001', '商品列表', '/shop/product', 'ck/shop/product/ShopProductList', 1, NULL, NULL, 1,
       NULL, '0', 1.00, 0, 'ant-design:appstore-outlined', 1, 0, 0, 0,
       NULL, 'admin', NOW(), NULL, NULL, 0, 0, NULL, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `id` = '2090000000000000002');

-- 品牌管理
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT '2090000000000000003', '2090000000000000001', '品牌管理', '/shop/brand', 'ck/shop/brand/ShopBrandList', 1, NULL, NULL, 1,
       NULL, '0', 2.00, 0, 'ant-design:tags-outlined', 1, 0, 0, 0,
       NULL, 'admin', NOW(), NULL, NULL, 0, 0, NULL, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `id` = '2090000000000000003');

-- 标签管理
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT '2090000000000000004', '2090000000000000001', '标签管理', '/shop/tag', 'ck/shop/tag/ShopTagList', 1, NULL, NULL, 1,
       NULL, '0', 3.00, 0, 'ant-design:bookmark-outlined', 1, 0, 0, 0,
       NULL, 'admin', NOW(), NULL, NULL, 0, 0, NULL, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `id` = '2090000000000000004');

-- -----------------------------------------------------------------------------
-- 2. 按钮权限：商品（父级 = 商品列表）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT t.id, '2090000000000000002', t.name, NULL, NULL, 0, NULL, NULL, 2,
       t.perms, '1', NULL, 0, NULL, 1, 0, 0, 0,
       NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM (
    SELECT '2090000000000000011' AS id, '新增商品' AS name, 'shop-product:add' AS perms
    UNION ALL SELECT '2090000000000000012', '编辑商品', 'shop-product:edit'
    UNION ALL SELECT '2090000000000000013', '删除商品', 'shop-product:delete'
    UNION ALL SELECT '2090000000000000014', '批量删除商品', 'shop-product:deleteBatch'
    UNION ALL SELECT '2090000000000000015', '导出商品', 'shop-product:exportXls'
    UNION ALL SELECT '2090000000000000016', '导入商品', 'shop-product:importExcel'
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.id);

-- -----------------------------------------------------------------------------
-- 3. 按钮权限：品牌（父级 = 品牌管理）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT t.id, '2090000000000000003', t.name, NULL, NULL, 0, NULL, NULL, 2,
       t.perms, '1', NULL, 0, NULL, 1, 0, 0, 0,
       NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM (
    SELECT '2090000000000000021' AS id, '新增品牌' AS name, 'shop-brand:add' AS perms
    UNION ALL SELECT '2090000000000000022', '编辑品牌', 'shop-brand:edit'
    UNION ALL SELECT '2090000000000000023', '删除品牌', 'shop-brand:delete'
    UNION ALL SELECT '2090000000000000024', '批量删除品牌', 'shop-brand:deleteBatch'
    UNION ALL SELECT '2090000000000000025', '导出品牌', 'shop-brand:exportXls'
    UNION ALL SELECT '2090000000000000026', '导入品牌', 'shop-brand:importExcel'
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.id);

-- -----------------------------------------------------------------------------
-- 4. 按钮权限：标签（父级 = 标签管理）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT t.id, '2090000000000000004', t.name, NULL, NULL, 0, NULL, NULL, 2,
       t.perms, '1', NULL, 0, NULL, 1, 0, 0, 0,
       NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM (
    SELECT '2090000000000000031' AS id, '新增标签' AS name, 'shop-tag:add' AS perms
    UNION ALL SELECT '2090000000000000032', '编辑标签', 'shop-tag:edit'
    UNION ALL SELECT '2090000000000000033', '删除标签', 'shop-tag:delete'
    UNION ALL SELECT '2090000000000000034', '批量删除标签', 'shop-tag:deleteBatch'
    UNION ALL SELECT '2090000000000000035', '导出标签', 'shop-tag:exportXls'
    UNION ALL SELECT '2090000000000000036', '导入标签', 'shop-tag:importExcel'
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.id);

-- -----------------------------------------------------------------------------
-- 5. 给 admin 角色授权（否则 BACK 权限模式下菜单不显示）
--    主键由 role_id + permission_id 的 MD5 派生，保证同一角色对同一权限只插一次
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('2', SUBSTRING(MD5(CONCAT('f6817f48af4fb3af11b9e8bf182f618b', p.id)), 1, 31)),
       'f6817f48af4fb3af11b9e8bf182f618b', p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
WHERE p.id LIKE '20900000000000%'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_permission` rp
      WHERE rp.role_id = 'f6817f48af4fb3af11b9e8bf182f618b' AND rp.permission_id = p.id
  );
