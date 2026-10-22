-- =============================================================================
-- 订单搜索索引：username / receiver_phone
-- 版本：3.9.5.20
--
-- 背景（清单 B9）：
--   后台订单列表的单一搜索框过去是 4 个 LIKE '%kw%' OR，username / receiver_name /
--   receiver_phone 都没有索引，order_no 的唯一索引也因前导 % 失效 —— 每次搜索全表扫描。
--   代码已改为"订单号等值 / 账号与手机号等值 / 收货人前缀"，这需要等值列上有索引。
--
-- 说明：MySQL 8 不支持 CREATE INDEX IF NOT EXISTS，本脚本由 Flyway 保证只执行一次。
-- -----------------------------------------------------------------------------

CREATE INDEX `idx_order_username` ON `ck_shop_order` (`username`);
CREATE INDEX `idx_order_receiver_phone` ON `ck_shop_order` (`receiver_phone`);
