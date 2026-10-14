-- =============================================================================
-- 购物车统计 菜单与按钮权限
-- 归属现有「商城」菜单（parent_id = 2079249150001979393，url = /shop）
--
-- 说明：
--   1. 权限 ID 使用 20900000000000000xx 号段，避开雪花 ID；0006 / 0061 当前空闲。
--   2. 幂等：NOT EXISTS 判断，可重复执行。
--   3. 菜单类型 menu_type=1（菜单），component 指向前端页面
--      src/views/ck/shop/cart/ShopCartStats.vue。
--   4. 按钮权限 shop-cart:stats 同时是**接口权限**：
--      /shop/cart/admin/** 全部接口都要求它。统计数据能反推各店转化情况，
--      只靠"菜单里看不见"来保护是不够的——直接调接口同样要挡住。
--   5. 给 admin 角色授权（BACK 权限模式下不授权则菜单不显示）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 菜单：购物车统计
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT '2090000000000000006', '2079249150001979393', '购物车统计', '/shop/cart-stats', 'ck/shop/cart/ShopCartStats', 1, NULL, NULL, 1,
       NULL, '0', 4.00, 0, 'ant-design:shopping-cart-outlined', 1, 0, 0, 0,
       '加购行为统计：加购次数/件数/去重人数、热度排行、按日趋势、加购明细', 'admin', NOW(), NULL, NULL, 0, 0, NULL, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `id` = '2090000000000000006');

-- -----------------------------------------------------------------------------
-- 2. 按钮权限（父级 = 购物车统计菜单）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT t.id, '2090000000000000006', t.name, NULL, NULL, 0, NULL, NULL, 2,
       t.perms, '1', NULL, 0, NULL, 1, 0, 0, 0,
       NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM (
    SELECT '2090000000000000061' AS id, '查看加购统计' AS name, 'shop-cart:stats' AS perms
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.id);

-- -----------------------------------------------------------------------------
-- 3. 给 admin 角色授权（否则 BACK 权限模式下菜单不显示、接口 403）
--    主键由 role_id + permission_id 的 MD5 派生，保证同一角色对同一权限只插一次
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('2', SUBSTRING(MD5(CONCAT('f6817f48af4fb3af11b9e8bf182f618b', p.id)), 1, 31)),
       'f6817f48af4fb3af11b9e8bf182f618b', p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
WHERE p.id IN ('2090000000000000006', '2090000000000000061')
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_permission` rp
      WHERE rp.role_id = 'f6817f48af4fb3af11b9e8bf182f618b' AND rp.permission_id = p.id
  );
