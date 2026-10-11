-- =============================================================================
-- 店铺管理 菜单与按钮权限
-- 归属现有「商品管理」目录（parent_id = 2090000000000000001）之下，作为其第 4 个子菜单
-- 说明：
--   1. 幂等：使用 NOT EXISTS 判断，可重复执行。
--   2. 权限 ID 沿用 209000000000000xxxx 号段（005 = 菜单，041-046 = 按钮）。
--   3. 同时给 admin 角色授权，否则 BACK 权限模式下菜单不显示。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 菜单：店铺管理
--    目录型/菜单型都要求 url 非空——url 为 NULL 会让前端路由构建崩溃
--    （joinParentPath 对 null 调 startsWith）。
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT '2090000000000000005', '2090000000000000001', '店铺管理', '/shop/store', 'ck/shop/store/ShopStoreList', 1, NULL, NULL, 1,
       NULL, '0', 4.00, 0, 'ant-design:shop-outlined', 1, 0, 0, 0,
       NULL, 'admin', NOW(), NULL, NULL, 0, 0, NULL, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `id` = '2090000000000000005');

-- -----------------------------------------------------------------------------
-- 2. 按钮权限（父级 = 店铺管理）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT t.id, '2090000000000000005', t.name, NULL, NULL, 0, NULL, NULL, 2,
       t.perms, '1', NULL, 0, NULL, 1, 0, 0, 0,
       NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM (
    SELECT '2090000000000000041' AS id, '新增店铺' AS name, 'shop-store:add' AS perms
    UNION ALL SELECT '2090000000000000042', '编辑店铺', 'shop-store:edit'
    UNION ALL SELECT '2090000000000000043', '删除店铺', 'shop-store:delete'
    UNION ALL SELECT '2090000000000000044', '批量删除店铺', 'shop-store:deleteBatch'
    UNION ALL SELECT '2090000000000000045', '导出店铺', 'shop-store:exportXls'
    UNION ALL SELECT '2090000000000000046', '导入店铺', 'shop-store:importExcel'
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.id);

-- -----------------------------------------------------------------------------
-- 3. 给 admin 角色授权
--    注意：OR 与 AND 混用时必须加括号，否则 AND 先结合，
--    "OR 之前"的那些行会绕过 NOT EXISTS 判定，重复执行时插入重复授权。
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('2', SUBSTRING(MD5(CONCAT('f6817f48af4fb3af11b9e8bf182f618b', p.id)), 1, 31)),
       'f6817f48af4fb3af11b9e8bf182f618b', p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
WHERE p.id IN ('2090000000000000005',
               '2090000000000000041', '2090000000000000042', '2090000000000000043',
               '2090000000000000044', '2090000000000000045', '2090000000000000046')
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_permission` rp
      WHERE rp.role_id = 'f6817f48af4fb3af11b9e8bf182f618b' AND rp.permission_id = p.id
  );
