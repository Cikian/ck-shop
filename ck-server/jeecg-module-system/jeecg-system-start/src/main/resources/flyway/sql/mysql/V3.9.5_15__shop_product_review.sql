-- =============================================================================
-- 商品评价 + 商品统计列"去假存真"
-- 版本：3.9.5.15
--
-- 背景（务必读）：
--   商品详情页的「已售 2068 / 4.5 / 收藏数 2653」全部来自 ck_shop_product 的冗余列，
--   而这几列在演示数据里是**手工写死的常量**，没有任何业务流水与之对应：
--     · sales_count / month_sales_count —— 只有下单支付链路在 +N，历史值是假的；
--     · review_count / rating_avg      —— 当时根本没有评价表，纯属编造；
--     · favorite_count                 —— 只有收藏 Service 在 ±1，历史值是假的。
--
-- 本脚本做两件事：
--   1) 新建真实评价表 ck_shop_product_review（一条订单明细只能评价一次）；
--   2) 把上述冗余列**按真实来源重算一遍**，让缓存与事实对齐。
--      此后所有店铺端读接口一律实时聚合同样来源（见 docs/真实统计与评价模块-接口契约.md），
--      冗余列只用于排序与后台统计，不再充当展示值。
--
-- 真实来源与口径：
--   销量      ck_shop_order_item.quantity ⋈ ck_shop_order（pay_status='PAID' 且订单未取消/关闭）
--   评价      ck_shop_product_review（status='Y' 且未删除）
--   收藏      ck_shop_favorite（del_flag=0）
--
-- 全部写成可重复执行（IF NOT EXISTS / 幂等 UPDATE）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `ck_shop_product_review` (
  `id`                 varchar(36)  NOT NULL COMMENT '主键',
  `product_id`         varchar(36)  NOT NULL COMMENT '商品ID(ck_shop_product.id)',
  `store_id`           varchar(36)  NOT NULL COMMENT '店铺ID(ck_shop_store.id)，评价按店铺隔离',
  `store_org_code`     varchar(64)  DEFAULT NULL COMMENT '评价时店铺绑定部门(快照)',
  `order_id`           varchar(36)  NOT NULL COMMENT '订单ID(ck_shop_order.id)',
  `order_no`           varchar(32)  DEFAULT NULL COMMENT '订单号(冗余，便于排查与展示)',
  `order_item_id`      varchar(36)  NOT NULL COMMENT '订单明细ID(ck_shop_order_item.id)；一条明细只能评价一次',
  `user_id`            varchar(36)  NOT NULL COMMENT '评价人ID(sys_user.id)',
  `username`           varchar(50)  DEFAULT NULL COMMENT '评价人账号(冗余快照)',
  `sku_combination_id` varchar(36)  DEFAULT NULL COMMENT '下单时的SKU组合ID快照',
  `sku_name`           varchar(255) DEFAULT NULL COMMENT '下单时的规格文案快照',
  `rating`             tinyint      NOT NULL COMMENT '评分 1-5',
  `content`            varchar(500) DEFAULT NULL COMMENT '评价内容(可空：允许只打分不写字)',
  `anonymous`          tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否匿名: 0-否 1-是',
  `status`             varchar(2)   NOT NULL DEFAULT 'Y' COMMENT '展示状态: Y-展示 N-后台隐藏(隐藏后不计入评分)',
  `create_by`          varchar(50)  DEFAULT NULL,
  `create_time`        datetime     DEFAULT NULL,
  `update_by`          varchar(50)  DEFAULT NULL,
  `update_time`        datetime     DEFAULT NULL,
  `del_flag`           tinyint(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  -- 评价资格的硬约束放在库里：同一条订单明细第二次提交必然撞键，
  -- 而不是依赖"先查一下有没有评价过"这种并发下会输的检查。
  UNIQUE KEY `uk_review_order_item` (`order_item_id`),
  -- 商品详情页恒为「某商品 + 未隐藏 + 未删除 + 按时间倒序」，索引按这个顺序建
  KEY `idx_review_product` (`product_id`,`status`,`del_flag`,`create_time`),
  -- 后台/客服按用户反查
  KEY `idx_review_user` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价(按订单明细唯一)';

-- -----------------------------------------------------------------------------
-- 冗余统计列按真实来源重算
--
-- 子查询只碰"别的表"（订单/收藏/评价），不与被更新的 ck_shop_product 自引用，
-- 因此不触发 MySQL 的 "You can't specify target table for update in FROM clause"。
-- -----------------------------------------------------------------------------
UPDATE `ck_shop_product`
SET `sales_count` = COALESCE((
        SELECT SUM(oi.`quantity` - COALESCE(oi.`refund_quantity`, 0))
        FROM `ck_shop_order_item` oi
        INNER JOIN `ck_shop_order` o ON o.`id` = oi.`order_id`
        WHERE oi.`product_id` = `ck_shop_product`.`id`
          AND o.`del_flag` = 0
          AND o.`pay_status` IN ('PAID', 'PARTIAL_REFUND')
          AND o.`order_status` NOT IN ('CANCELLED', 'CLOSED')
    ), 0),
    `month_sales_count` = COALESCE((
        SELECT SUM(oi.`quantity` - COALESCE(oi.`refund_quantity`, 0))
        FROM `ck_shop_order_item` oi
        INNER JOIN `ck_shop_order` o ON o.`id` = oi.`order_id`
        WHERE oi.`product_id` = `ck_shop_product`.`id`
          AND o.`del_flag` = 0
          AND o.`pay_status` IN ('PAID', 'PARTIAL_REFUND')
          AND o.`order_status` NOT IN ('CANCELLED', 'CLOSED')
          AND o.`pay_time` >= DATE_SUB(NOW(), INTERVAL 30 DAY)
    ), 0),
    `favorite_count` = COALESCE((
        SELECT COUNT(*)
        FROM `ck_shop_favorite` f
        WHERE f.`product_id` = `ck_shop_product`.`id`
          AND f.`del_flag` = 0
    ), 0),
    `review_count` = COALESCE((
        SELECT COUNT(*)
        FROM `ck_shop_product_review` r
        WHERE r.`product_id` = `ck_shop_product`.`id`
          AND r.`del_flag` = 0
          AND r.`status` = 'Y'
    ), 0),
    `rating_avg` = COALESCE((
        SELECT ROUND(AVG(r.`rating`), 1)
        FROM `ck_shop_product_review` r
        WHERE r.`product_id` = `ck_shop_product`.`id`
          AND r.`del_flag` = 0
          AND r.`status` = 'Y'
    ), 0)
WHERE `del_flag` = 0;
