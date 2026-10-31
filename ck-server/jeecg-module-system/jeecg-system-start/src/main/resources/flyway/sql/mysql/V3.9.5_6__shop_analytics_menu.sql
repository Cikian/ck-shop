-- =============================================================================
-- 用户行为分析 · 菜单与操作权限（Flyway 版）
--
-- 与 ck-server/db/ck-shop-analytics-menu.sql 内容一致；手工执行请用 db/ 下同名脚本。
-- =============================================================================

INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT t.id, t.parent_id, t.name, t.url, t.component, 1, NULL, NULL, 1,
       NULL, '0', t.sort_no, 0, t.icon, 1, 0, 0, 0,
       t.description, 'admin', NOW(), NULL, NULL, 0, 0, NULL, 0
FROM (
    SELECT '2090000000000000010' AS id, '2079249150001979393' AS parent_id,
           '行为大屏' AS name, '/shop/analytics/bigscreen' AS url, 'ck/shop/analytics/BigScreen' AS component,
           8.00 AS sort_no, 'ant-design:fund-view-outlined' AS icon,
           '实时大屏：在线人数/PV/UV/实时订单/地域滚动' AS description
    UNION ALL
    SELECT '2090000000000000017', '2079249150001979393',
           '行为仪表盘', '/shop/analytics/dashboard', 'ck/shop/analytics/Dashboard',
           9.00, 'ant-design:dashboard-outlined',
           'KPI/趋势/漏斗/商品排行'
    UNION ALL
    SELECT '2090000000000000018', '2079249150001979393',
           '行为明细', '/shop/analytics/detail', 'ck/shop/analytics/Detail',
           10.00, 'ant-design:unordered-list-outlined',
           '行为明细分页查询/导出'
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.id);

INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT t.id, t.parent_id, t.name, NULL, NULL, 0, NULL, NULL, 2,
       t.perms, '1', NULL, 0, NULL, 1, 0, 0, 0,
       t.description, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM (
    SELECT '2090000000000000131' AS id, '2090000000000000010' AS parent_id,
           '查看行为统计' AS name, 'shop-analytics:view' AS perms,
           '查看实时大盘/趋势/漏斗/排行/地域/来源/设备/页面/明细' AS description
    UNION ALL SELECT '2090000000000000132', '2090000000000000018',
           '导出明细', 'shop-analytics:export',
           '导出行为明细 CSV（Phase 1 仅 gate 前端导出按钮，全量导出列 Phase 2）'
    UNION ALL SELECT '2090000000000000133', '2090000000000000010',
           '全局汇总视角', 'shop-analytics:all',
           '绕过 sys_org_code 范围查看全局汇总（仅授超管/总部）'
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.id);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('1', SUBSTRING(MD5(CONCAT(r.`id`, p.`id`)), 1, 31)),
       r.`id`, p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
JOIN `sys_role` r ON r.`role_code` = 'admin'
WHERE p.`id` IN (
    '2090000000000000010', '2090000000000000017', '2090000000000000018',
    '2090000000000000131', '2090000000000000132', '2090000000000000133'
)
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_permission` rp
      WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
