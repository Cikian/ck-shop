-- =============================================================================
-- 收货地址 / 订单地址：支持「国家 + 州省 + 城市」级联，并明确"快照哪一层语言"
--
-- 背景：
--   店铺面向东南亚、中亚、一带一路国家，不做国内生意。原地址表只有
--   province/city/district 三个自由文本，既无法校验也无法按国分区（运费/时效），
--   海外用户还常把国家名写进"省"里。
--
-- 两条设计原则：
--   1) **编码 + 名称双轨**：country_code / province_code / city_id 是权威（用于校验、
--      统计、运费分区、物流对接）；province / city / district 文字是下单那一刻的快照
--      （地区数据以后怎么改，历史地址都不会变）。
--   2) **快照存当地语言**：快递员只看当地文字。所以 province/city 存的是
--      region 数据里的 native（当地文字）而非界面语言译名。界面展示要哪种语言，
--      是用 code 反查 translations 现算的，不落库。
--
-- 兼容：
--   · 三个文字列保持原语义（自由文本仍可用——自贸区、工业园这类地址任何数据集都没有），
--     只是新数据由服务端用 region 表覆盖成标准当地名。
--   · 存量行 country_code 为 NULL = 老数据，前端按老格式渲染。
--   · 长度从 varchar(50) 放宽到 100：泰文/俄文/阿拉伯文的正式地名比中文长得多，
--     50 在严格模式下会直接报错而不是静默截断。
-- =============================================================================

ALTER TABLE `ck_shop_user_address`
  MODIFY COLUMN `province` varchar(100) DEFAULT NULL COMMENT '省/州(当地语言快照)',
  MODIFY COLUMN `city`     varchar(100) DEFAULT NULL COMMENT '市(当地语言快照)',
  MODIFY COLUMN `district` varchar(100) DEFAULT NULL COMMENT '区/县(当地语言快照,可空)',
  ADD COLUMN `country_code`  char(2)            DEFAULT NULL COMMENT 'ISO 3166-1 alpha-2（权威）' AFTER `receiver_phone`,
  ADD COLUMN `country_name`  varchar(100)       DEFAULT NULL COMMENT '国名(当地语言快照)'          AFTER `country_code`,
  ADD COLUMN `province_code` varchar(10)        DEFAULT NULL COMMENT 'ISO 3166-2（权威）'          AFTER `country_name`,
  ADD COLUMN `city_id`       mediumint unsigned DEFAULT NULL COMMENT 'cities.id（权威）'           AFTER `city`,
  ADD KEY `idx_addr_country` (`country_code`);

ALTER TABLE `ck_shop_order`
  MODIFY COLUMN `receiver_province` varchar(100) DEFAULT NULL COMMENT '省/州(当地语言快照)',
  MODIFY COLUMN `receiver_city`     varchar(100) DEFAULT NULL COMMENT '市(当地语言快照)',
  MODIFY COLUMN `receiver_district` varchar(100) DEFAULT NULL COMMENT '区/县(当地语言快照)',
  ADD COLUMN `receiver_country_code`  char(2)            DEFAULT NULL COMMENT '国家ISO码(快照)'        AFTER `receiver_phone`,
  ADD COLUMN `receiver_country`       varchar(100)       DEFAULT NULL COMMENT '国名(当地语言快照)'      AFTER `receiver_country_code`,
  ADD COLUMN `receiver_province_code` varchar(10)        DEFAULT NULL COMMENT '省州ISO 3166-2(快照)'   AFTER `receiver_country`,
  ADD COLUMN `receiver_city_id`       mediumint unsigned DEFAULT NULL COMMENT 'cities.id(快照)'         AFTER `receiver_province_code`,
  ADD KEY `idx_order_country` (`receiver_country_code`);
