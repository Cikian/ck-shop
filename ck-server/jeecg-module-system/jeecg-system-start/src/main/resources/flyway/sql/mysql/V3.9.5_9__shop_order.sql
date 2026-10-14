-- =============================================================================
-- 订单 + 支付 + 退款 · 数据模型
-- 版本：3.9.5.9
--
-- 设计总纲（对应 docs/订单支付相关问题.md 的「核心应对原则」）：
--
--   1. **订单 / 支付单 / 退款单 三单分离**
--      订单表只表达"买卖关系"，支付单表只表达"资金收付"，退款单表只表达"资金退回"。
--      三者用 order_no / payment_no / refund_no 唯一绑定，任何一方都能独立对账。
--      把支付信息塞进订单表是掉单的根源：一笔订单多次支付、部分退款时就无处安放。
--
--   2. **状态机严格 + CAS 更新**
--      所有订单都带 version 乐观锁。状态流转一律走
--        UPDATE ... SET order_status=?, version=version+1 WHERE id=? AND order_status=? AND version=?
--      影响行数为 0 即视为并发冲突，直接失败——**绝不"读出来判断一下再写回去"**。
--
--   3. **金额只用 DECIMAL，不用 FLOAT/DOUBLE**
--      所有金额列一律 decimal(12,2)。Java 侧一律 BigDecimal。
--      浮点算钱必然出现"对账差 1 分"，这个错误一旦发生无法追溯修复。
--
--   4. **快照优先：订单是历史凭证，不是商品表的视图**
--      商品名、规格、图、单价、成本价、分类、店铺名、收货地址，全部在下单那一刻**复制**进订单。
--      否则运营改一次商品名 / 用户改一次地址，历史订单就变了，售后与审计无从谈起。
--
--   5. **只追加的账本表不设 del_flag**
--      ck_shop_order_event（操作流水）、ck_shop_payment_notify_log（回调流水）、
--      ck_shop_stock_log（库存流水）、ck_shop_fund_flow（资金流水）四张表**刻意不带删除标记**：
--      账本条目被"删除"本身就是数据完整性问题。要纠错只能追加一条反向流水。
--
--   6. **订单严禁物理删除**
--      主表 del_flag 只作为「归档」标记（0-正常 / 1-已归档），且仅允许
--      已取消(CANCELLED)/已关闭(CLOSED) 的订单归档；有效订单（待支付/待发货/已发货/已完成）
--      Java 层不提供任何删除入口，归档接口也会硬校验状态。
--
--   7. **隔离维度双保险**
--      商品/分类用 sys_org_code（部门编码）隔离，促销用 store_id 隔离。
--      订单**两个都存**：store_id（业务归属）+ store_org_code（数据权限维度），
--      并且明细行也冗余一份，避免统计时回表 join 造成跨店串数据。
--
-- 全部写成可重复执行。
-- =============================================================================


-- =============================================================================
-- 0. 已有表增列：库存预占
-- =============================================================================
-- 下单即"预占"库存（frozen_stock），支付成功才把 stock 真正扣掉；
-- 取消/超时关单只释放 frozen_stock。这样既不会超卖，也不会因为大量未付款订单
-- 把真实库存吃掉。
--
-- 预占用带条件的原子 UPDATE 完成，不依赖读-改-写：
--   UPDATE ... SET frozen_stock = frozen_stock + #{qty}
--    WHERE id = #{id} AND stock - frozen_stock >= #{qty}
-- 影响行数 = 0 即"库存不足"，天然抗并发，不需要分布式锁。

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ck_shop_product` ADD COLUMN `frozen_stock` int(11) NOT NULL DEFAULT 0 COMMENT ''预占库存(下单占用, 支付后转实扣)'' AFTER `stock`',
    'SELECT ''ck_shop_product.frozen_stock 已存在，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ck_shop_product' AND COLUMN_NAME = 'frozen_stock');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ck_shop_product_sku_combination` ADD COLUMN `frozen_stock` int(11) NOT NULL DEFAULT 0 COMMENT ''预占库存(下单占用, 支付后转实扣)'' AFTER `stock`',
    'SELECT ''ck_shop_product_sku_combination.frozen_stock 已存在，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ck_shop_product_sku_combination' AND COLUMN_NAME = 'frozen_stock');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- =============================================================================
-- 0.2 已有表增列：商品成本价
-- =============================================================================
-- 订单明细里的 cost_price / cost_amount 与订单上的 cost_amount / gross_profit
-- 都依赖商品侧有成本价来源。没有这一列时毛利只能恒为 0，
-- "毛利统计"就是假的——比不提供更危险，因为运营会拿它做定价决策。
--
-- 因此在这里把成本价列补齐：
--   - SKU 成本价优先（不同规格成本可能不同，例如大容量内存条更贵）；
--   - SKU 未填时回退商品级成本价；
--   - 都未填时为 0，此时毛利偏高，属于**显式已知**的口径
--     （比"编一个成本"要诚实得多）。
-- 运营在【商品管理】的商品/SKU 编辑里填入成本价后，该口径立即生效。

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ck_shop_product` ADD COLUMN `cost_price` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT ''成本价(毛利统计; 0=未录入)'' AFTER `price`',
    'SELECT ''ck_shop_product.cost_price 已存在，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ck_shop_product' AND COLUMN_NAME = 'cost_price');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ck_shop_product_sku_combination` ADD COLUMN `cost_price` decimal(12,2) DEFAULT NULL COMMENT ''成本价(毛利统计; NULL=回退商品成本价)'' AFTER `price`',
    'SELECT ''ck_shop_product_sku_combination.cost_price 已存在，跳过'' AS skipped')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ck_shop_product_sku_combination' AND COLUMN_NAME = 'cost_price');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- =============================================================================
-- 1. 用户收货地址簿
-- =============================================================================
-- 地址是**用户级**数据，不按店铺隔离：同一个人在 A 店和 B 店用的是同一个收货地址。
-- 但订单里必须存一份完整快照——用户改地址不能篡改历史订单的收货信息。
CREATE TABLE IF NOT EXISTS `ck_shop_user_address` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `user_id` varchar(36) NOT NULL COMMENT '用户ID(sys_user.id)',
  `username` varchar(50) DEFAULT NULL COMMENT '用户账号(冗余, 后台展示免回表)',
  `receiver_name` varchar(50) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(30) NOT NULL COMMENT '收货人手机号(明文存储, 接口按场景脱敏返回)',
  `province` varchar(50) DEFAULT NULL COMMENT '省',
  `city` varchar(50) DEFAULT NULL COMMENT '市',
  `district` varchar(50) DEFAULT NULL COMMENT '区/县',
  `detail_address` varchar(255) NOT NULL COMMENT '详细地址',
  `postal_code` varchar(20) DEFAULT NULL COMMENT '邮政编码',
  `address_tag` varchar(20) DEFAULT NULL COMMENT '地址标签: 家/公司/学校',
  `is_default` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否默认地址: 0-否, 1-是',
  -- 统计：哪个地址被用得最多，可用于智能排序；也是"地址被改过"的排查线索
  `use_count` int(11) NOT NULL DEFAULT 0 COMMENT '被使用下单次数',
  `last_used_time` datetime DEFAULT NULL COMMENT '最近一次下单使用时间',
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记: 0-正常, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_addr_user` (`user_id`,`del_flag`,`is_default`),
  KEY `idx_addr_user_used` (`user_id`,`last_used_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址簿';


-- =============================================================================
-- 2. 订单主表（一店一单）
-- =============================================================================
CREATE TABLE IF NOT EXISTS `ck_shop_order` (
  `id` varchar(36) NOT NULL COMMENT '主键',

  -- ---------- 标识 ----------
  `order_no` varchar(32) NOT NULL COMMENT '订单号(业务唯一, 时间戳+随机, 非自增以免被爬和泄露单量)',
  `parent_order_no` varchar(32) DEFAULT NULL COMMENT '父订单号(跨店合单/拆单预留, 当前恒为空)',
  `split_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否子单: 0-否, 1-是(预留)',
  `idempotent_key` varchar(64) DEFAULT NULL COMMENT '下单幂等键(客户端生成的UUID, 重复提交返回同一笔订单)',

  -- ---------- 归属（双维度隔离） ----------
  `store_id` varchar(36) NOT NULL COMMENT '店铺ID(ck_shop_store.id)',
  `store_name` varchar(100) DEFAULT NULL COMMENT '店铺名称快照(店铺改名不影响历史订单)',
  `store_org_code` varchar(64) NOT NULL COMMENT '店铺归属部门编码快照(数据权限隔离维度)',

  -- ---------- 用户 ----------
  `user_id` varchar(36) NOT NULL COMMENT '用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户账号(冗余, 后台列表免回表)',

  -- ---------- 状态 ----------
  `order_status` varchar(20) NOT NULL DEFAULT 'WAIT_PAY'
      COMMENT '订单状态: WAIT_PAY待支付/WAIT_SHIP待发货/SHIPPED已发货/COMPLETED已完成/CANCELLED已取消/CLOSED已关闭(超时)',
  `pay_status` varchar(20) NOT NULL DEFAULT 'UNPAID'
      COMMENT '支付状态: UNPAID未支付/PAID已支付/PARTIAL_REFUND部分退款/REFUNDED已全额退款',
  `refund_status` varchar(20) NOT NULL DEFAULT 'NONE'
      COMMENT '退款状态: NONE无/APPLYING申请中/REJECTED已拒绝/PARTIAL部分退款/FULL全额退款',
  `version` int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁版本(状态CAS更新, 禁止读-改-写)',

  -- ---------- 金额（全部 decimal(12,2)，禁止浮点） ----------
  `total_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '商品原价合计(吊牌价×数量)',
  `product_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '商品成交金额合计(促销优惠前)',
  `promotion_discount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '促销活动优惠合计',
  `coupon_discount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '优惠券抵扣(预留, 当前恒为0)',
  `points_discount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '积分抵扣(预留, 当前恒为0)',
  `member_discount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '会员优惠(预留, 当前恒为0)',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '运费',
  `other_fee` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '其它费用(预留, 当前恒为0)',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '优惠合计(促销+券+积分+会员; 冗余落库以便统计口径唯一)',
  `pay_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '应付金额 = product_amount - discount_amount + freight_amount + other_fee',
  `paid_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '已支付金额(以支付单成功金额回写, 不由前端传)',
  `refund_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计已退款金额(退款成功时累加, 退款额度校验的唯一依据)',
  `cost_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '商品成本合计(毛利统计)',
  `gross_profit` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '毛利(统计冗余) = product_amount - discount_amount - cost_amount',
  `currency` varchar(8) NOT NULL DEFAULT 'CNY' COMMENT '币种',

  -- ---------- 数量（统计维度） ----------
  `item_count` int(11) NOT NULL DEFAULT 0 COMMENT '商品种类数(明细行数)',
  `total_quantity` int(11) NOT NULL DEFAULT 0 COMMENT '商品总件数',
  `total_weight` decimal(12,3) NOT NULL DEFAULT 0.000 COMMENT '总重量(kg, 预留运费计算)',

  -- ---------- 收货地址快照（下单那一刻的副本，用户改地址不影响历史订单） ----------
  `receiver_name` varchar(50) DEFAULT NULL COMMENT '收货人姓名(快照)',
  `receiver_phone` varchar(30) DEFAULT NULL COMMENT '收货人手机号(快照)',
  `receiver_province` varchar(50) DEFAULT NULL COMMENT '省(快照)',
  `receiver_city` varchar(50) DEFAULT NULL COMMENT '市(快照)',
  `receiver_district` varchar(50) DEFAULT NULL COMMENT '区/县(快照)',
  `receiver_address` varchar(255) DEFAULT NULL COMMENT '详细地址(快照)',
  `receiver_postal_code` varchar(20) DEFAULT NULL COMMENT '邮编(快照)',
  `receiver_tag` varchar(20) DEFAULT NULL COMMENT '地址标签(快照)',
  `address_snapshot` text COMMENT '完整地址JSON快照(含地址簿ID与全部字段, 便于事后核对)',

  -- ---------- 支付 ----------
  `pay_channel` varchar(32) DEFAULT NULL COMMENT '支付渠道: MOCK/WECHAT/ALIPAY(预留)',
  `pay_type` varchar(32) DEFAULT NULL COMMENT '支付方式细分: JSAPI/NATIVE/H5/APP(预留)',
  `payment_no` varchar(64) DEFAULT NULL COMMENT '最近一笔支付单号(便于从订单直达支付单)',
  `pay_deadline` datetime DEFAULT NULL COMMENT '支付截止时间(超时关单的唯一依据)',
  `pay_time` datetime DEFAULT NULL COMMENT '支付成功时间',
  `ship_time` datetime DEFAULT NULL COMMENT '发货时间',
  `receive_time` datetime DEFAULT NULL COMMENT '确认收货时间',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `close_time` datetime DEFAULT NULL COMMENT '超时关闭时间',
  `finish_time` datetime DEFAULT NULL COMMENT '进入终态的时间(取消/关闭/完成)',

  -- ---------- 物流 ----------
  `logistics_company` varchar(64) DEFAULT NULL COMMENT '物流公司',
  `logistics_no` varchar(64) DEFAULT NULL COMMENT '物流单号',
  `ship_remark` varchar(255) DEFAULT NULL COMMENT '发货备注',

  -- ---------- 取消 ----------
  `cancel_type` varchar(20) DEFAULT NULL COMMENT '取消来源: USER用户/ADMIN后台/TIMEOUT超时/REFUND退款关闭',
  `cancel_reason` varchar(255) DEFAULT NULL COMMENT '取消原因',

  -- ---------- 来源与统计 ----------
  `order_source` varchar(20) DEFAULT NULL COMMENT '下单来源: web/h5/app/api',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '下单IP(风控/异常排查)',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent(风控/异常排查)',
  `referrer` varchar(500) DEFAULT NULL COMMENT '来源页面',
  `channel_code` varchar(64) DEFAULT NULL COMMENT '渠道/推广位编码(统计)',
  `utm_source` varchar(64) DEFAULT NULL COMMENT 'UTM来源(统计)',

  -- ---------- 备注 ----------
  `buyer_remark` varchar(500) DEFAULT NULL COMMENT '买家备注',
  `seller_remark` varchar(500) DEFAULT NULL COMMENT '卖家备注(后台填写)',

  -- ---------- 审计 ----------
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint(1) NOT NULL DEFAULT 0
      COMMENT '归档标记: 0-正常, 1-已归档(仅已取消/已关闭订单可归档; 严禁物理删除)',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  -- 幂等键可空；MySQL 唯一索引里 NULL 彼此不相等，所以不传幂等键时不会互相阻塞
  UNIQUE KEY `uk_order_idempotent` (`idempotent_key`),
  KEY `idx_order_user_status_time` (`user_id`,`order_status`,`create_time`),
  KEY `idx_order_store_status_time` (`store_id`,`order_status`,`create_time`),
  KEY `idx_order_org_time` (`store_org_code`,`create_time`),
  -- 超时关单扫描专用：只扫 WAIT_PAY 且 pay_deadline 已过
  KEY `idx_order_status_deadline` (`order_status`,`pay_deadline`),
  KEY `idx_order_store_paytime` (`store_id`,`pay_time`),
  KEY `idx_order_create_time` (`create_time`),
  KEY `idx_order_parent` (`parent_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表(一店一单, 严禁物理删除)';


-- =============================================================================
-- 3. 订单明细（商品快照，只读凭证）
-- =============================================================================
CREATE TABLE IF NOT EXISTS `ck_shop_order_item` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `order_id` varchar(36) NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号(冗余, 便于按单号直查明细)',
  `store_id` varchar(36) NOT NULL COMMENT '店铺ID(冗余, 统计免回表)',
  `store_org_code` varchar(64) NOT NULL COMMENT '店铺部门编码(冗余, 数据权限过滤免回表)',
  `user_id` varchar(36) NOT NULL COMMENT '用户ID(冗余, 统计免回表)',

  -- ---------- 商品快照 ----------
  `product_id` varchar(36) NOT NULL COMMENT '商品ID(仅作追溯线索, 展示一律用快照)',
  `sku_id` varchar(36) NOT NULL DEFAULT '' COMMENT 'SKU组合ID; 无规格商品为空串(与购物车口径一致)',
  `spu_code` varchar(64) DEFAULT NULL COMMENT 'SPU编码快照',
  `sku_code` varchar(64) DEFAULT NULL COMMENT 'SKU编码快照',
  `product_name` varchar(255) NOT NULL COMMENT '商品名称快照',
  `sku_name` varchar(255) DEFAULT NULL COMMENT '规格文案快照 如 白色/8G/256G',
  `product_image` varchar(500) DEFAULT NULL COMMENT '商品主图快照',
  `unit` varchar(20) DEFAULT NULL COMMENT '单位快照',
  `category_id` varchar(36) DEFAULT NULL COMMENT '分类ID快照(按类目统计)',
  `category_name` varchar(100) DEFAULT NULL COMMENT '分类名称快照(按类目统计)',
  `weight` decimal(12,3) NOT NULL DEFAULT 0.000 COMMENT '单件重量(kg, 预留)',

  -- ---------- 价格快照 ----------
  `original_price` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '原价/划线价(下单时)',
  `price` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '成交单价(下单时, 服务端算出并锁定)',
  `quantity` int(11) NOT NULL DEFAULT 0 COMMENT '购买数量',
  `total_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '原价小计 = original_price × quantity',
  `product_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '成交小计 = price × quantity',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '本行优惠金额(从促销分摊而来)',
  `actual_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '本行实付 = product_amount - discount_amount',
  `cost_price` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '成本单价快照',
  `cost_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '成本小计 = cost_price × quantity',

  -- ---------- 命中的促销（快照，用于事后解释"为什么这个价"） ----------
  `promotion_id` varchar(36) DEFAULT NULL COMMENT '命中的促销活动ID',
  `promotion_type` varchar(20) DEFAULT NULL COMMENT '促销类型快照: DIRECT/PERCENT/FULL_CUT/SECKILL',
  `promotion_title` varchar(200) DEFAULT NULL COMMENT '促销名称快照',
  `promotion_discount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '本行促销优惠',
  `promotion_instance_id` varchar(64) DEFAULT NULL COMMENT '促销实例标识(秒杀场次等, 用于限量回补)',

  -- ---------- 售后 ----------
  `refund_status` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '行退款状态: NONE/PARTIAL/FULL',
  `refund_quantity` int(11) NOT NULL DEFAULT 0 COMMENT '已退数量',
  `refund_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '已退金额(含运费分摊)',

  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记: 0-正常, 1-已归档',

  PRIMARY KEY (`id`),
  KEY `idx_item_order` (`order_id`),
  KEY `idx_item_order_no` (`order_no`),
  KEY `idx_item_product` (`product_id`,`create_time`),
  KEY `idx_item_sku` (`sku_id`,`create_time`),
  KEY `idx_item_store_time` (`store_id`,`create_time`),
  KEY `idx_item_org_time` (`store_org_code`,`create_time`),
  KEY `idx_item_category` (`category_id`,`create_time`),
  KEY `idx_item_promotion` (`promotion_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细(商品快照)';


-- =============================================================================
-- 4. 支付单（与订单分离；一笔订单可有多笔支付尝试）
-- =============================================================================
CREATE TABLE IF NOT EXISTS `ck_shop_order_payment` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `payment_no` varchar(64) NOT NULL COMMENT '支付单号(本系统唯一, 即渠道 out_trade_no)',
  `order_id` varchar(36) NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号(冗余)',
  `user_id` varchar(36) NOT NULL COMMENT '用户ID',
  `store_id` varchar(36) NOT NULL COMMENT '店铺ID',
  `store_org_code` varchar(64) NOT NULL COMMENT '店铺部门编码',

  `channel` varchar(32) NOT NULL COMMENT '支付渠道: MOCK/WECHAT/ALIPAY',
  `pay_type` varchar(32) DEFAULT NULL COMMENT '支付方式细分: JSAPI/NATIVE/H5/APP',
  `amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '应付金额(下单时订单应付额, 服务端写入)',
  `paid_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '渠道实收金额(以渠道回调为准)',
  `refund_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计已退金额(退款额度校验依据)',
  `currency` varchar(8) NOT NULL DEFAULT 'CNY' COMMENT '币种',

  `status` varchar(20) NOT NULL DEFAULT 'INIT'
      COMMENT '支付单状态: INIT初始化/PENDING待支付/SUCCESS成功/FAILED失败/CLOSED已关闭',
  `channel_trade_no` varchar(64) DEFAULT NULL COMMENT '渠道流水号(渠道侧唯一, 对账依据)',
  `channel_buyer_id` varchar(128) DEFAULT NULL COMMENT '渠道付款方标识(预留)',
  `expire_time` datetime DEFAULT NULL COMMENT '支付单过期时间(与订单 pay_deadline 一致)',
  `pay_time` datetime DEFAULT NULL COMMENT '渠道确认支付成功时间',
  `close_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `notify_time` datetime DEFAULT NULL COMMENT '最近一次收到渠道回调的时间',
  `notify_count` int(11) NOT NULL DEFAULT 0 COMMENT '累计收到回调次数(排查重复回调)',
  `last_notify_body` text COMMENT '最近一次回调报文(脱敏后保存, 排查用)',
  `error_code` varchar(64) DEFAULT NULL COMMENT '失败错误码',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `request_id` varchar(64) DEFAULT NULL COMMENT '发起支付幂等键(同一订单并发发起只留一笔有效支付单)',
  `version` int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',

  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记: 0-正常, 1-已归档',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  KEY `idx_payment_order` (`order_id`),
  KEY `idx_payment_order_no` (`order_no`),
  KEY `idx_payment_channel_trade` (`channel`,`channel_trade_no`),
  -- 补单任务扫描：长时间 PENDING 的支付单要主动去渠道查
  KEY `idx_payment_status_time` (`status`,`create_time`),
  KEY `idx_payment_org_time` (`store_org_code`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单(与订单分离, 资金收付的唯一载体)';


-- =============================================================================
-- 5. 退款单（一笔支付可多次部分退款，额度由支付单 CAS 锁死）
-- =============================================================================
CREATE TABLE IF NOT EXISTS `ck_shop_order_refund` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `refund_no` varchar(64) NOT NULL COMMENT '退款单号(本系统唯一, 即渠道 out_refund_no)',
  `order_id` varchar(36) NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号(冗余)',
  `payment_id` varchar(36) DEFAULT NULL COMMENT '支付单ID(全额退款必须绑定)',
  `payment_no` varchar(64) DEFAULT NULL COMMENT '支付单号(冗余)',
  `user_id` varchar(36) NOT NULL COMMENT '用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户账号',
  `store_id` varchar(36) NOT NULL COMMENT '店铺ID',
  `store_org_code` varchar(64) NOT NULL COMMENT '店铺部门编码',

  `refund_type` varchar(20) NOT NULL DEFAULT 'FULL' COMMENT '退款类型: FULL全额/PARTIAL部分',
  `refund_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '申请退款金额',
  `item_count` int(11) NOT NULL DEFAULT 0 COMMENT '退款商品种类数',
  `total_quantity` int(11) NOT NULL DEFAULT 0 COMMENT '退款商品件数',

  `apply_reason` varchar(100) DEFAULT NULL COMMENT '申请原因(字典 shop_refund_reason)',
  `apply_desc` varchar(500) DEFAULT NULL COMMENT '申请说明',
  `apply_images` text COMMENT '凭证图片(JSON数组)',

  `status` varchar(20) NOT NULL DEFAULT 'APPLYING'
      COMMENT '退款状态: APPLYING待审核/REJECTED已拒绝/REFUNDING退款中/SUCCESS退款成功/FAILED退款失败/CLOSED已撤销',
  `apply_time` datetime DEFAULT NULL COMMENT '申请时间',
  `audit_by` varchar(50) DEFAULT NULL COMMENT '审核人',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_remark` varchar(500) DEFAULT NULL COMMENT '审核备注',
  `reject_reason` varchar(255) DEFAULT NULL COMMENT '拒绝原因',

  `channel_refund_no` varchar(64) DEFAULT NULL COMMENT '渠道退款流水号(对账依据)',
  `refund_time` datetime DEFAULT NULL COMMENT '退款到账时间',
  `fail_code` varchar(64) DEFAULT NULL COMMENT '退款失败码',
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '退款失败原因',
  `retry_count` int(11) NOT NULL DEFAULT 0 COMMENT '渠道退款重试次数',

  `request_id` varchar(64) DEFAULT NULL COMMENT '幂等键(防重复提交退款申请)',
  `version` int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',

  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记: 0-正常, 1-已归档',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  UNIQUE KEY `uk_refund_request` (`request_id`),
  KEY `idx_refund_order` (`order_id`),
  KEY `idx_refund_order_no` (`order_no`),
  KEY `idx_refund_payment` (`payment_no`),
  KEY `idx_refund_status_time` (`status`,`create_time`),
  KEY `idx_refund_org_time` (`store_org_code`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单(累计退款额度受支付单CAS约束)';


-- =============================================================================
-- 6. 退款明细（按订单行退，支撑部分退款）
-- =============================================================================
CREATE TABLE IF NOT EXISTS `ck_shop_order_refund_item` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `refund_id` varchar(36) NOT NULL COMMENT '退款单ID',
  `refund_no` varchar(64) NOT NULL COMMENT '退款单号(冗余)',
  `order_id` varchar(36) NOT NULL COMMENT '订单ID',
  `order_item_id` varchar(36) NOT NULL COMMENT '订单明细ID',
  `product_id` varchar(36) DEFAULT NULL COMMENT '商品ID',
  `sku_id` varchar(36) NOT NULL DEFAULT '' COMMENT 'SKU组合ID; 无规格为空串',
  `product_name` varchar(255) DEFAULT NULL COMMENT '商品名称快照',
  `sku_name` varchar(255) DEFAULT NULL COMMENT '规格快照',
  `price` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '退款单价(成交价快照)',
  `quantity` int(11) NOT NULL DEFAULT 0 COMMENT '退款数量',
  `refund_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '本行退款金额',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_refund_item_refund` (`refund_id`),
  KEY `idx_refund_item_order_item` (`order_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款明细';


-- =============================================================================
-- 7. 订单操作流水（只追加，不设 del_flag）
-- =============================================================================
-- 这张表是"数据可追溯"的落点：谁、什么时候、从什么状态、到什么状态、改了什么。
-- 后台改价、退款、补单、关单全都必须留下一条。出事故时先看这张表。
CREATE TABLE IF NOT EXISTS `ck_shop_order_event` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `order_id` varchar(36) NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号(冗余)',
  `store_id` varchar(36) DEFAULT NULL COMMENT '店铺ID',
  `store_org_code` varchar(64) DEFAULT NULL COMMENT '店铺部门编码',

  `event_type` varchar(32) NOT NULL COMMENT '事件类型: CREATE/PAY_REQUEST/PAY_SUCCESS/PAY_FAIL/CANCEL/CLOSE/SHIP/CONFIRM/RECEIVE/ARCHIVE/REMARK/REFUND_APPLY/REFUND_AUDIT/REFUND_SUCCESS/REFUND_FAIL/COMPENSATE',
  `from_status` varchar(20) DEFAULT NULL COMMENT '变更前订单状态',
  `to_status` varchar(20) DEFAULT NULL COMMENT '变更后订单状态',
  `from_pay_status` varchar(20) DEFAULT NULL COMMENT '变更前支付状态',
  `to_pay_status` varchar(20) DEFAULT NULL COMMENT '变更后支付状态',

  `operator_type` varchar(16) NOT NULL COMMENT '操作者类型: USER/ADMIN/SYSTEM/CHANNEL',
  `operator_id` varchar(36) DEFAULT NULL COMMENT '操作者ID(管理员为用户ID, 渠道为渠道名)',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '操作者名称',

  `amount` decimal(12,2) DEFAULT NULL COMMENT '本次事件涉及金额(支付/退款金额)',
  `remark` varchar(500) DEFAULT NULL COMMENT '事件说明',
  `detail` text COMMENT '事件明细JSON(金额快照/渠道报文摘要/物流信息等)',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '来源IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '来源UA',
  `create_time` datetime DEFAULT NULL COMMENT '发生时间',

  PRIMARY KEY (`id`),
  KEY `idx_event_order` (`order_id`,`create_time`),
  KEY `idx_event_order_no` (`order_no`,`create_time`),
  KEY `idx_event_type_time` (`event_type`,`create_time`),
  KEY `idx_event_org_time` (`store_org_code`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单操作流水(只追加, 可追溯; 刻意不设删除标记)';


-- =============================================================================
-- 8. 渠道回调流水（幂等 + 防重放 + 对账依据；只追加）
-- =============================================================================
CREATE TABLE IF NOT EXISTS `ck_shop_payment_notify_log` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `channel` varchar(32) NOT NULL COMMENT '支付渠道: MOCK/WECHAT/ALIPAY',
  `notify_type` varchar(16) NOT NULL COMMENT '回调类型: PAY支付/REFUND退款',
  `payment_no` varchar(64) DEFAULT NULL COMMENT '支付单号(本系统)',
  `refund_no` varchar(64) DEFAULT NULL COMMENT '退款单号(退款回调时)',
  `channel_trade_no` varchar(64) DEFAULT NULL COMMENT '渠道流水号',
  -- 去重键：同一个渠道的同一次事件重复推送（渠道普遍会重推 5~8 次）必须只被处理一次。
  -- 组成：channel + 事件类型 + 渠道流水号 + 渠道事件时间/状态，由代码计算后写入唯一索引。
  `dedup_key` varchar(160) NOT NULL COMMENT '回调去重键(唯一索引, 保证重复回调只处理一次)',
  `raw_body` text COMMENT '回调原始报文(脱敏后保存)',
  `signature` varchar(512) DEFAULT NULL COMMENT '渠道签名',
  `sign_verified` tinyint(1) NOT NULL DEFAULT 0 COMMENT '验签结果: 0-未通过/未验签, 1-通过',
  `process_status` varchar(20) NOT NULL DEFAULT 'RECEIVED'
      COMMENT '处理结果: RECEIVED已接收/PROCESSED已处理/DUPLICATE重复回调忽略/REJECTED验签失败/FAILED处理失败',
  `process_msg` varchar(500) DEFAULT NULL COMMENT '处理说明/失败原因',
  `notify_time` datetime DEFAULT NULL COMMENT '渠道事件时间(防重放校验依据之一)',
  `receive_time` datetime DEFAULT NULL COMMENT '接收时间',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '回调来源IP(渠道IP白名单校验可基于此)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notify_dedup` (`dedup_key`),
  KEY `idx_notify_payment` (`payment_no`,`receive_time`),
  KEY `idx_notify_channel_time` (`channel`,`receive_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道回调流水(幂等去重, 只追加; 刻意不设删除标记)';


-- =============================================================================
-- 9. 库存流水（只追加）—— 库存对不上时，这是唯一能查清的东西
-- =============================================================================
-- 一次下单会写两条：OCCUPY(预占) ；支付成功写 DEDUCT(实扣) + 隐含释放预占；
-- 取消/关单写 RELEASE；退款回补写 RESTORE。
-- before/after 双写，任何时刻都能用"最后一条的 after_stock"与商品表实际值比对，
-- 从而自动发现"谁把库存改坏了"。
CREATE TABLE IF NOT EXISTS `ck_shop_stock_log` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `store_id` varchar(36) DEFAULT NULL COMMENT '店铺ID',
  `product_id` varchar(36) NOT NULL COMMENT '商品ID',
  `sku_id` varchar(36) NOT NULL DEFAULT '' COMMENT 'SKU组合ID; 无规格为空串',

  `change_type` varchar(20) NOT NULL
      COMMENT '变动类型: OCCUPY下单预占/RELEASE释放预占/DEDUCT支付实扣/RESTORE退款或取消回补/ADJUST人工调整',
  `quantity` int(11) NOT NULL COMMENT '变动数量(预占类为正, 释放类为负)',
  `stock_delta` int(11) NOT NULL DEFAULT 0 COMMENT '对 stock 列的净变动',
  `frozen_delta` int(11) NOT NULL DEFAULT 0 COMMENT '对 frozen_stock 列的净变动',
  `before_stock` int(11) NOT NULL DEFAULT 0 COMMENT '变动前实际库存',
  `after_stock` int(11) NOT NULL DEFAULT 0 COMMENT '变动后实际库存',
  `before_frozen` int(11) NOT NULL DEFAULT 0 COMMENT '变动前预占库存',
  `after_frozen` int(11) NOT NULL DEFAULT 0 COMMENT '变动后预占库存',

  `order_no` varchar(32) DEFAULT NULL COMMENT '关联订单号',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '关联业务ID(支付单号/退款单号)',
  `operator` varchar(50) DEFAULT NULL COMMENT '操作者(用户名或 SYSTEM)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stock_log_sku_time` (`product_id`,`sku_id`,`create_time`),
  KEY `idx_stock_log_order` (`order_no`),
  KEY `idx_stock_log_type_time` (`change_type`,`create_time`),
  KEY `idx_stock_log_biz` (`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水(只追加; 刻意不设删除标记)';


-- =============================================================================
-- 10. 资金流水（只追加）—— 资金准确性的唯一账本
-- =============================================================================
-- 与订单/支付/退款解耦：无论钱是怎么进来的（支付回调、主动查询补单、人工补单），
-- 只要资金发生变动就必须在这里留一条。对账时用它跟渠道流水逐笔比对。
CREATE TABLE IF NOT EXISTS `ck_shop_fund_flow` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `flow_no` varchar(64) NOT NULL COMMENT '资金流水号(唯一)',
  `biz_type` varchar(20) NOT NULL COMMENT '业务类型: PAY_IN收款/REFUND_OUT退款',
  `order_no` varchar(32) DEFAULT NULL COMMENT '订单号',
  `payment_no` varchar(64) DEFAULT NULL COMMENT '支付单号',
  `refund_no` varchar(64) DEFAULT NULL COMMENT '退款单号',
  `store_id` varchar(36) DEFAULT NULL COMMENT '店铺ID',
  `store_org_code` varchar(64) DEFAULT NULL COMMENT '店铺部门编码',
  `user_id` varchar(36) DEFAULT NULL COMMENT '用户ID',

  `channel` varchar(32) DEFAULT NULL COMMENT '渠道: MOCK/WECHAT/ALIPAY',
  `channel_trade_no` varchar(64) DEFAULT NULL COMMENT '渠道流水号(对账主键)',
  `amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '金额(带符号: 收款为正, 退款为负)',
  `direction` varchar(8) NOT NULL COMMENT '资金方向: IN流入/OUT流出',
  `currency` varchar(8) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `status` varchar(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '流水状态: SUCCESS/FAILED',
  `trade_time` datetime DEFAULT NULL COMMENT '渠道交易时间(对账按此归日, 不用本地时间以免跨日切错位)',
  `sync_time` datetime DEFAULT NULL COMMENT '入账时间(本地)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fund_flow_no` (`flow_no`),
  -- 同一笔支付/退款在资金侧只能有一条成功流水：业务幂等失效时，这里是最后一道防线
  UNIQUE KEY `uk_fund_biz` (`biz_type`,`channel`,`channel_trade_no`),
  KEY `idx_fund_order` (`order_no`),
  KEY `idx_fund_payment` (`payment_no`),
  KEY `idx_fund_type_time` (`biz_type`,`trade_time`),
  KEY `idx_fund_org_time` (`store_org_code`,`trade_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金流水(只追加; 刻意不设删除标记)';


-- =============================================================================
-- 11. 促销用户额度（防"限购绕过"）
-- =============================================================================
-- 每人限购不能只靠"查订单明细求和再判断"：两个人同时下单、或同一用户开两个标签页，
-- 都会在"查完还没写"的窗口里超限。这里用唯一键 (promotion_id, user_id) 把
-- "一条用户额度记录"变成行锁载体，再用带条件的原子 UPDATE 累加：
--   UPDATE ... SET used_quantity = used_quantity + #{qty}
--    WHERE promotion_id=? AND user_id=? AND used_quantity + #{qty} <= #{limit}
-- 影响行数 = 0 即超限。不存在"读-判断-写"的竞态。
CREATE TABLE IF NOT EXISTS `ck_shop_promotion_user_quota` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `promotion_id` varchar(36) NOT NULL COMMENT '促销活动ID',
  `user_id` varchar(36) NOT NULL COMMENT '用户ID',
  `store_id` varchar(36) DEFAULT NULL COMMENT '店铺ID',
  `used_quantity` int(11) NOT NULL DEFAULT 0 COMMENT '该用户在该活动下已占用数量',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_promo_user` (`promotion_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='促销用户额度(限购防绕过, 只追加累加不删除)';


-- =============================================================================
-- 12. 退款原因字典（前端下拉/后台展示共用）
-- =============================================================================
INSERT INTO `sys_dict` (`id`, `dict_name`, `dict_code`, `description`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `type`)
SELECT '9000000000000000200', '订单退款原因', 'shop_refund_reason', '订单售后申请原因', 0, 'admin', NOW(), 'admin', NOW(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict` WHERE `dict_code` = 'shop_refund_reason');

SET @refund_dict_id := (SELECT `id` FROM `sys_dict` WHERE `dict_code` = 'shop_refund_reason' LIMIT 1);

INSERT INTO `sys_dict_item` (`id`, `dict_id`, `item_text`, `item_value`, `description`, `sort_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT t.id, @refund_dict_id, t.item_text, t.item_value, t.description, t.sort_order, 1, 'admin', NOW(), 'admin', NOW()
FROM (
  SELECT '9000000000000000201' AS id, '不想要了' AS item_text, 'no_need' AS item_value, NULL AS description, 1 AS sort_order
  UNION ALL SELECT '9000000000000000202', '拍错了/重复下单', 'duplicate', NULL, 2
  UNION ALL SELECT '9000000000000000203', '商品与描述不符', 'mismatch', NULL, 3
  UNION ALL SELECT '9000000000000000204', '商品破损/质量问题', 'quality', NULL, 4
  UNION ALL SELECT '9000000000000000205', '少件/漏发', 'missing', NULL, 5
  UNION ALL SELECT '9000000000000000206', '物流问题', 'logistics', NULL, 6
  UNION ALL SELECT '9000000000000000207', '其它', 'other', NULL, 99
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_item` i WHERE i.`id` = t.id);
