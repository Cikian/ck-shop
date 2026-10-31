-- =============================================================================
-- 用户行为分析 · 数据模型（Flyway 版）
--
-- 与 ck-server/db/ck-shop-analytics.sql 内容一致；手工执行请用 db/ 下同名脚本。
-- 唯一权威契约：docs/用户行为分析-接口契约.md §3.2 / §3.3。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `ck_shop_track_event` (
  `id` varchar(36) NOT NULL COMMENT '主键(雪花)',
  `event_time` datetime(3) NOT NULL COMMENT '事件发生时间(毫秒)',
  `event_name` varchar(64) NOT NULL COMMENT '事件名(受控枚举, 见契约§4.1)',
  `session_id` varchar(64) DEFAULT NULL COMMENT '会话ID(BFF生成, 写cookie)',
  `visitor_id` varchar(64) DEFAULT NULL COMMENT '匿名访客ID(BFF生成随机标识, 非登录态也唯一)',
  `user_id` varchar(36) DEFAULT NULL COMMENT '登录用户ID(可空, 登录态才回填)',
  `org_code` varchar(64) DEFAULT NULL COMMENT '店铺归属部门编码(快照, sys_org_code语义)',
  `store_id` varchar(36) DEFAULT NULL COMMENT '店铺ID(可空, 首页/列表可能无店)',
  `product_id` varchar(36) DEFAULT NULL COMMENT '商品ID(商品类事件必填, 其余可空)',
  `page_url` varchar(512) DEFAULT NULL COMMENT '完整页面URL',
  `page_path` varchar(256) DEFAULT NULL COMMENT '归一化路径(去query, 用于页面排行)',
  `referrer` varchar(512) DEFAULT NULL COMMENT '来源页(首跳直接访问为空)',
  `utm_source` varchar(128) DEFAULT NULL COMMENT '渠道归因UTM来源',
  `utm_medium` varchar(128) DEFAULT NULL COMMENT '渠道归因UTM媒介',
  `utm_campaign` varchar(128) DEFAULT NULL COMMENT '渠道归因UTM活动',
  `device_type` varchar(16) DEFAULT NULL COMMENT '设备类型: desktop/mobile/tablet(UA判定)',
  `os` varchar(64) DEFAULT NULL COMMENT '操作系统(UA解析)',
  `browser` varchar(64) DEFAULT NULL COMMENT '浏览器(UA解析)',
  `screen` varchar(32) DEFAULT NULL COMMENT '屏幕分辨率(客户端事件才有)',
  `lang` varchar(16) DEFAULT NULL COMMENT '界面语言(en/zh/fr)',
  `country_code` varchar(8) DEFAULT NULL COMMENT 'ISO 3166-1 alpha-2',
  `country_name` varchar(64) DEFAULT NULL COMMENT '国名(GeoLite2)',
  `province_name` varchar(64) DEFAULT NULL COMMENT '省/州名(GeoLite2)',
  `city_name` varchar(64) DEFAULT NULL COMMENT '城市名(GeoLite2, 海外到城市级)',
  `province_code` varchar(16) DEFAULT NULL COMMENT 'ISO 3166-2(海外无省时为空)',
  `ip` varchar(45) DEFAULT NULL COMMENT '原始客户端IP(本期不脱敏, IPv4/IPv6)',
  `duration_ms` int(11) DEFAULT NULL COMMENT '停留时长(仅page_leave事件, 单位ms)',
  `props` json DEFAULT NULL COMMENT '扩展属性(搜索词/曝光位置/点击元素/结果数/无结果标志)',
  PRIMARY KEY (`id`),
  KEY `idx_event_time` (`event_time`),
  KEY `idx_org_event_time` (`org_code`, `event_time`),
  KEY `idx_product_event` (`product_id`, `event_time`),
  KEY `idx_event_name_time` (`event_name`, `event_time`),
  KEY `idx_session` (`session_id`),
  KEY `idx_visitor` (`visitor_id`, `event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为明细(append-only账本, 刻意不设删除标记, 按天归档)';

CREATE TABLE IF NOT EXISTS `ck_shop_track_summary` (
  `id` varchar(36) NOT NULL COMMENT '主键',
  `stat_time` datetime NOT NULL COMMENT '统计桶起点(整点或整天)',
  `granularity` varchar(8) NOT NULL COMMENT '粒度: hour/day',
  `org_code` varchar(64) NOT NULL DEFAULT 'ALL' COMMENT '店铺归属; ALL=全局(超管视角)',
  `product_id` varchar(36) NOT NULL DEFAULT 'ALL' COMMENT '商品维度; ALL=非商品维度',
  `dim_type` varchar(32) NOT NULL COMMENT '维度: global/store/product/region/source/device/page',
  `dim_value` varchar(128) NOT NULL COMMENT '维度取值(国家码/来源渠道/页面路径/商品ID冗余等)',
  `pv` bigint(20) NOT NULL DEFAULT 0 COMMENT '浏览量',
  `uv` bigint(20) NOT NULL DEFAULT 0 COMMENT '去重访客数',
  `ip_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '去重IP数',
  `visit_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '会话数',
  `bounce_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '跳出会话数',
  `total_duration_ms` bigint(20) NOT NULL DEFAULT 0 COMMENT '停留总时长(求和)',
  `expose_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '曝光数(商品维度)',
  `click_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '点击数(商品维度)',
  `add_cart_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '加购数(从业务流水回填)',
  `order_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '下单数(从业务流水回填)',
  `paid_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '支付数(从业务流水回填)',
  `order_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '下单额(商品维度, 供转化与GMV关联)',
  `paid_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '实收额(商品维度, 供转化与GMV关联)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_summary` (`stat_time`, `granularity`, `org_code`, `product_id`, `dim_type`, `dim_value`),
  KEY `idx_summary_time_dim` (`stat_time`, `granularity`, `dim_type`),
  KEY `idx_summary_org_time` (`org_code`, `stat_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为多维度汇总(小时/日, 长期保留)';
