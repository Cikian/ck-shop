-- =============================================================================
-- 订单模块 · 菜单与操作权限
-- 版本：3.9.5.10
--
-- 归属现有「商城」菜单（parent_id = 2079249150001979393，url = /shop）
--
-- 号段（接在购物车统计 …0006 / …0061 之后）：
--   菜单  …0007  订单管理   /shop/order          ck/shop/order/ShopOrderList
--         …0008  退款管理   /shop/refund         ck/shop/refund/ShopRefundList
--         …0009  订单统计   /shop/order-stats    ck/shop/order/ShopOrderStats
--   按钮  …0101~0107  订单管理下的操作权限
--         …0111~0113  退款管理下的操作权限
--         …0121       订单统计查看权限
--
-- **号段为什么从 0071 挪到 0101**：最初选的 …0071~0077 与促销活动模块
-- （…0071 菜单 + …0072~0080 按钮）**撞号**了。撞号的后果非常隐蔽：
-- 脚本里用的是 `WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = ?)`，
-- 撞号的几条会被静默跳过，脚本 exit code 仍然是 0、日志也不报错，
-- 只有在验证"权限是否真的建出来了"时才会发现少了 7 条。
-- 教训：号段必须**先查库确认空闲**再选，不能凭记忆。
-- 现在订单模块独占 …0101~…0121，与已有的 …0001~…0091 之间留出整段空隙，
-- 后续促销/品牌等模块继续用 …007x~…009x 也不会再撞。
--
-- 设计要点：
--   1. **菜单权限与接口权限用同一个权限码**。只靠"菜单里看不见"来保护是不够的：
--      订单列表包含收货人手机号（已脱敏）、金额、渠道流水号，
--      直接调接口同样必须挡住。因此后端每个接口都打了 @RequiresPermissions，
--      权限码与这里的按钮权限一一对应。
--
--   2. **刻意没有「删除订单」权限码**。订单严禁物理删除，有效订单在逻辑层面
--      也禁止删除——这个能力在接口层就不存在，因此不需要（也不应该有）对应权限。
--      需要清理列表时用「归档」，且只对已取消/已关闭订单开放。
--
--   3. **退款审核与退款重试拆成两个权限**。审核是"同意给不给退"的决策，
--      重试只是重发一次渠道请求。把它们合成一个权限，会让"只能重试"的
--      一线客服也拥有审批权。
--
--   4. **补单权限单独授予**。人工补单会改动订单状态与资金记账，
--      属于资金操作，应当只给值班/技术负责人。
--
-- 全部语句写成可重复执行。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 菜单（menu_type = 1）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT t.id, t.parent_id, t.name, t.url, t.component, 1, NULL, NULL, 1,
       NULL, '0', t.sort_no, 0, t.icon, 1, 0, 0, 0,
       t.description, 'admin', NOW(), NULL, NULL, 0, 0, NULL, 0
FROM (
    SELECT '2090000000000000007' AS id, '2079249150001979393' AS parent_id,
           '订单管理' AS name, '/shop/order' AS url, 'ck/shop/order/ShopOrderList' AS component,
           5.00 AS sort_no, 'ant-design:profile-outlined' AS icon,
           '订单列表/详情/发货/关闭/备注/归档/人工补单' AS description
    UNION ALL
    SELECT '2090000000000000008', '2079249150001979393',
           '退款管理', '/shop/refund', 'ck/shop/refund/ShopRefundList',
           6.00, 'ant-design:rollback-outlined',
           '退款单列表/详情/审核/渠道退款重试'
    UNION ALL
    SELECT '2090000000000000009', '2079249150001979393',
           '订单统计', '/shop/order-stats', 'ck/shop/order/ShopOrderStats',
           7.00, 'ant-design:bar-chart-outlined',
           'GMV/实收/退款/净收入/转化率/热销排行/风险指标'
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.id);

-- -----------------------------------------------------------------------------
-- 2. 订单管理：操作权限（menu_type = 2）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT t.id, '2090000000000000007', t.name, NULL, NULL, 0, NULL, NULL, 2,
       t.perms, '1', NULL, 0, NULL, 1, 0, 0, 0,
       t.description, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM (
    SELECT '2090000000000000101' AS id, '查看订单列表' AS name, 'shop-order:list' AS perms,
           '订单分页列表（含收货人手机号，接口已脱敏）' AS description
    UNION ALL SELECT '2090000000000000102', '查看订单详情', 'shop-order:detail',
           '订单详情：商品快照/金额构成/成本毛利/支付单/退款单/完整操作流水'
    UNION ALL SELECT '2090000000000000103', '订单发货', 'shop-order:ship',
           '标记发货并填写物流公司与单号'
    UNION ALL SELECT '2090000000000000104', '关闭订单', 'shop-order:close',
           '关闭订单；已支付订单必须先完成全额退款，否则接口拒绝'
    UNION ALL SELECT '2090000000000000105', '修改卖家备注', 'shop-order:remark',
           '填写/修改卖家备注（会写入操作流水）'
    UNION ALL SELECT '2090000000000000106', '订单归档', 'shop-order:archive',
           '仅已取消/已关闭订单可归档；归档只影响列表可见性，数据完整保留，永不物理删除'
    UNION ALL SELECT '2090000000000000107', '人工补单', 'shop-order:compensate',
           '按订单号或支付单号主动向渠道查询并补正状态（资金操作，仅授予值班/技术负责人）'
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.id);

-- -----------------------------------------------------------------------------
-- 3. 退款管理：操作权限
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT t.id, '2090000000000000008', t.name, NULL, NULL, 0, NULL, NULL, 2,
       t.perms, '1', NULL, 0, NULL, 1, 0, 0, 0,
       t.description, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM (
    SELECT '2090000000000000111' AS id, '查看退款单' AS name, 'shop-refund:list' AS perms,
           '退款单列表与详情、某订单可退金额查询' AS description
    UNION ALL SELECT '2090000000000000112', '退款审核', 'shop-refund:audit',
           '审核退款申请：通过即锁定支付单退款额度并提交渠道退款；拒绝必须填原因'
    UNION ALL SELECT '2090000000000000113', '重试渠道退款', 'shop-refund:retry',
           '对【退款失败】或【退款中】的退款单重新提交渠道退款'
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.id);

-- -----------------------------------------------------------------------------
-- 4. 订单统计：查看权限
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
(`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`,
 `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`,
 `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
SELECT t.id, '2090000000000000009', t.name, NULL, NULL, 0, NULL, NULL, 2,
       t.perms, '1', NULL, 0, NULL, 1, 0, 0, 0,
       t.description, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0
FROM (
    SELECT '2090000000000000121' AS id, '查看订单统计' AS name, 'shop-order:stats' AS perms,
           '订单看板：GMV/实收/退款/净收入/转化率/热销排行/风险指标（手动触发定时任务需要 shop-order:compensate）' AS description
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` p WHERE p.`id` = t.id);

-- -----------------------------------------------------------------------------
-- 5. 授给 admin 角色
--    必须授：BACK 权限模式下不授权则菜单不显示，且后端接口直接 403。
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT CONCAT('1', SUBSTRING(MD5(CONCAT(r.`id`, p.`id`)), 1, 31)),
       r.`id`, p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_permission` p
JOIN `sys_role` r ON r.`role_code` = 'admin'
WHERE p.`id` IN (
    '2090000000000000007', '2090000000000000008', '2090000000000000009',
    '2090000000000000101', '2090000000000000102', '2090000000000000103',
    '2090000000000000104', '2090000000000000105', '2090000000000000106',
    '2090000000000000107',
    '2090000000000000111', '2090000000000000112', '2090000000000000113',
    '2090000000000000121'
)
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_permission` rp
      WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
