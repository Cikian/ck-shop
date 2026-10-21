-- =============================================================================
-- 管理端商城读接口权限码（shop-*:list）
-- 版本：3.9.5.17
--
-- 背景：
--   商品/促销/分类/标签/轮播图的后台「列表、详情」接口过去没有 @RequiresPermissions。
--   JeecgBoot 没有 URL 级权限过滤器（菜单可见 ≠ 接口可调），任何已登录后台账号
--   ——甚至店铺端买家 token——直连后端就能读到全部商品、活动、分类与轮播图配置。
--
-- 新增 5 个按钮权限：
--   …081  shop-product:list         商品列表/详情/SKU 组合
--   …082  shop-promotion:list       促销活动列表/详情
--   …083  shop-category:list        商品分类列表/详情
--   …084  shop-tag:list             标签列表/详情
--   …085  ck:shop:slideshow:list    轮播图列表（管理端）
--
-- 授权策略（保持现有可见性，不额外放开）：
--   * 每个 :list 授给"已拥有对应模块菜单"的角色；
--   * 跨模块依赖也要授：
--       - 商品列表/详情被"促销选商品"复用 → 有促销菜单的角色也要给 shop-product:list；
--       - 分类/标签列表被"商品表单"复用     → 有商品菜单的角色也要给 shop-category:list / shop-tag:list；
--   * admin 角色必定授。
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
SELECT t.id, t.parent_id, t.name, NULL, NULL, 0, NULL, NULL, 2,
       t.perms, '1', NULL, 0, NULL, 1, 0, 0, 0,
       t.description, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM (
    SELECT '2090000000000000081' AS id, '2090000000000000002' AS parent_id,
           '查看商品' AS name, 'shop-product:list' AS perms,
           '商品管理：查看商品列表/详情/SKU 组合（含促销选商品）' AS description
    UNION ALL
    SELECT '2090000000000000082', '2090000000000000071',
           '查看活动', 'shop-promotion:list',
           '促销活动：查看活动列表与详情'
    UNION ALL
    SELECT '2090000000000000083', '2083967093235429378',
           '查看分类', 'shop-category:list',
           '商品分类：查看分类列表与详情（商品表单依赖）'
    UNION ALL
    SELECT '2090000000000000084', '2090000000000000004',
           '查看标签', 'shop-tag:list',
           '标签管理：查看标签列表与详情（商品表单依赖）'
    UNION ALL
    SELECT '2090000000000000085', '2079249942289227777',
           '查看轮播图', 'ck:shop:slideshow:list',
           '轮播图：查看管理端轮播图列表'
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.id);

-- -----------------------------------------------------------------------------
-- 2. 授权（按"角色已有哪个模块的菜单"来授，含跨模块依赖）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('9', SUBSTRING(MD5(CONCAT(r.`id`, p.`id`)), 1, 31)),
       r.`id`, p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
JOIN `sys_role` r ON r.`role_code` = 'admin'
WHERE p.`id` IN ('2090000000000000081','2090000000000000082',
                 '2090000000000000083','2090000000000000084','2090000000000000085')
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_permission` rp
      WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('9', SUBSTRING(MD5(CONCAT(g.`role_id`, g.`permission_id`)), 1, 31)),
       g.`role_id`, g.`permission_id`, NULL, NOW(), '127.0.0.1'
FROM (
    -- 同一个角色可能同时命中多条规则（如既有商品菜单又有促销菜单），必须去重，
    -- 否则同一条 (role_id, permission_id) 会被 INSERT 两次（第二行撞主键）。
    SELECT DISTINCT g0.role_id, g0.permission_id FROM (
        -- 有商品菜单(002) → 分类/标签列表要能读
        SELECT rp.`role_id` AS role_id, '2090000000000000083' AS permission_id
          FROM `sys_role_permission` rp WHERE rp.`permission_id` = '2090000000000000002'
        UNION ALL
        SELECT rp.`role_id`, '2090000000000000084'
          FROM `sys_role_permission` rp WHERE rp.`permission_id` = '2090000000000000002'
        -- 有促销菜单(071) → 选商品要能读商品列表/SKU
        UNION ALL
        SELECT rp.`role_id`, '2090000000000000081'
          FROM `sys_role_permission` rp WHERE rp.`permission_id` = '2090000000000000071'
        -- 模块自身菜单 → 对应 :list
        UNION ALL
        SELECT rp.`role_id`, '2090000000000000081'
          FROM `sys_role_permission` rp WHERE rp.`permission_id` = '2090000000000000002'
        UNION ALL
        SELECT rp.`role_id`, '2090000000000000082'
          FROM `sys_role_permission` rp WHERE rp.`permission_id` = '2090000000000000071'
        UNION ALL
        SELECT rp.`role_id`, '2090000000000000083'
          FROM `sys_role_permission` rp WHERE rp.`permission_id` = '2083967093235429378'
        UNION ALL
        SELECT rp.`role_id`, '2090000000000000084'
          FROM `sys_role_permission` rp WHERE rp.`permission_id` = '2090000000000000004'
        UNION ALL
        SELECT rp.`role_id`, '2090000000000000085'
          FROM `sys_role_permission` rp WHERE rp.`permission_id` = '2079249942289227777'
    ) g0
) g
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    WHERE rp.`role_id` = g.`role_id` AND rp.`permission_id` = g.`permission_id`
);
