-- =============================================================================
-- 「店铺-查看」按钮权限：shop-store:list
-- 版本：3.9.5.16
--
-- 背景：
--   /shop/store/list 与 /shop/store/queryById 返回的是**完整实体**
--   （departId / departOrgCode / domain / themeConfig / createBy），
--   但这两个接口过去没有 @RequiresPermissions。JeecgBoot 没有 URL 级权限过滤器，
--   "菜单可见"不等于"接口可调"——任何已登录账号（包括店铺端买家 token）直连后端
--   就能枚举全部店铺的内部组织编码与域名。
--
-- 处理：
--   ① 新增按钮权限 shop-store:list（菜单「店铺管理」下）；
--   ② 授给"已经拥有店铺管理菜单"的角色（保持现有可见性不变），admin 角色必定包含。
--
-- 号段：接在店铺按钮 …041~047 之后 → …048
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
SELECT '2090000000000000048', '2090000000000000005', '查看店铺列表', NULL, NULL, 0, NULL, NULL, 2,
       'shop-store:list', '1', NULL, 0, NULL, 1, 0, 0, 0,
       '店铺管理：查看店铺列表与详情（返回完整店铺配置，故需独立权限）',
       'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `id` = '2090000000000000048');

-- -----------------------------------------------------------------------------
-- 2. 授权：给"当前已拥有店铺管理菜单"的角色（沿用现有可见性，不额外放开）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('9', SUBSTRING(MD5(CONCAT(r.`id`, p.`id`)), 1, 31)),
       r.`id`, p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
JOIN `sys_role` r ON 1 = 1
WHERE p.`id` = '2090000000000000048'
  AND (
        r.`role_code` = 'admin'
        OR EXISTS (
            SELECT 1 FROM `sys_role_permission` rp
            WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = '2090000000000000005'
        )
      )
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_permission` rp
      WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
