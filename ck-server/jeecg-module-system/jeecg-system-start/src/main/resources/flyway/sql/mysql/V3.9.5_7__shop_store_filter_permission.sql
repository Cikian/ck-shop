-- =============================================================================
-- 「按店铺筛选」按钮权限
-- 版本：3.9.5.7
--
-- 背景：
--   多店铺改造后，商品列表 / 商品分类列表的「所属店铺」筛选只对"能跨店看数据"的账号有意义。
--   运营账号即使看到这个下拉，选别的店铺也只会得到空结果（后端可见域取交集，不会放大范围），
--   属于纯 UI 噪声；这里把它收成独立的按钮权限，方便按角色授予。
--
-- 号段（新增，接在店铺按钮 …041~047 之后）：
--   …057  商品列表 - 按店铺筛选   shop-product:filter-store
--   …058  商品分类 - 按店铺筛选   shop-category:filter-store
--
-- 授权策略：
--   授给 admin 角色（当前唯一的"总部/管理员"角色）。**必须授**——否则连管理员都看不到这个筛选。
--   后续新建的"大区/总部"类角色可按需勾选，运营角色不勾。
--
-- 全部语句写成可重复执行。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 按钮权限（menu_type=2）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT t.id, t.parent_id, t.name, NULL, NULL, 0, NULL, NULL, 2,
       t.perms, '1', NULL, 0, NULL, 1, 0, 0, 0,
       t.description, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM (
    SELECT '2090000000000000057' AS id, '2090000000000000002' AS parent_id,
           '按店铺筛选' AS name, 'shop-product:filter-store' AS perms,
           '商品列表：显示「所属店铺」筛选下拉（跨店查看能力）' AS description
    UNION ALL
    SELECT '2090000000000000058', '2083967093235429378',
           '按店铺筛选', 'shop-category:filter-store',
           '商品分类列表：显示「所属店铺」筛选下拉（跨店查看能力）'
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.id);

-- -----------------------------------------------------------------------------
-- 2. 授给 admin 角色
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('9', SUBSTRING(MD5(CONCAT(r.`id`, p.`id`)), 1, 31)),
       r.`id`, p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
JOIN `sys_role` r ON r.`role_code` = 'admin'
WHERE p.`id` IN ('2090000000000000057', '2090000000000000058')
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_permission` rp
      WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
