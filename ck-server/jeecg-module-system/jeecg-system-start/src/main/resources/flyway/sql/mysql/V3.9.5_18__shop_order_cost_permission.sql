-- =============================================================================
-- 「订单-成本/毛利」字段级权限：shop-order:cost
-- 版本：3.9.5.18
--
-- 背景：
--   订单详情/列表/统计接口返回 costAmount、grossProfit、grossProfitRate，
--   这些是经营敏感信息，但过去只受页面级 shop-order:detail / shop-order:stats 控制。
--   任何有详情权限的运营都能看到成本与毛利（甚至绕过前端直接调接口）。
--
-- 处理：
--   ① 新增按钮权限 shop-order:cost（菜单「订单管理」下）；
--   ② **只授给 admin 角色**：其余角色默认看不到，需要由管理员在「角色管理」里显式勾选。
--      这与其它权限"沿用现有可见性"的策略不同 —— 成本数据默认不可见才是安全的默认值。
--   ③ 后端控制器在无该权限时把字段置空（见 ShopCostPermission）；
--      前端按同一权限码隐藏卡片，两层一致。
--
-- 号段：接在订单统计 …121 之后 → …122
--
-- 全部语句可重复执行。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 按钮权限（menu_type=2）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT '2090000000000000122', '2090000000000000007', '查看成本与毛利', NULL, NULL, 0, NULL, NULL, 2,
       'shop-order:cost', '1', NULL, 0, NULL, 1, 0, 0, 0,
       '订单成本/毛利字段级权限：无此权限时后端返回空值、前端隐藏',
       'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `id` = '2090000000000000122');

-- -----------------------------------------------------------------------------
-- 2. 授权：仅 admin 角色默认拥有（其它角色需显式勾选）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('9', SUBSTRING(MD5(CONCAT(r.`id`, p.`id`)), 1, 31)),
       r.`id`, p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
JOIN `sys_role` r ON r.`role_code` = 'admin'
WHERE p.`id` = '2090000000000000122'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_permission` rp
      WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
