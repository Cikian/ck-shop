-- =============================================================================
-- 订单搜索索引：username / receiver_phone
-- 版本：3.9.5.20
--
-- 背景（清单 B9）：
--   后台订单列表的单一搜索框过去是 4 个 LIKE '%kw%' OR，username / receiver_name /
--   receiver_phone 都没有索引，order_no 的唯一索引也因前导 % 失效 —— 每次搜索全表扫描。
--   代码已改为"订单号等值 / 账号与手机号等值 / 收货人前缀"，这需要等值列上有索引。
--
-- 为什么写成"先查 information_schema 再决定是否执行"：
--   MySQL 8 不支持 CREATE INDEX IF NOT EXISTS，而这两个索引在部分环境可能已被手工
--   创建过。幂等写法保证迁移不会因 "Duplicate key name" 失败（清单 B16）。
-- -----------------------------------------------------------------------------

SET @idx_user := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'ck_shop_order'
      AND index_name = 'idx_order_username'
);

SET @ddl_user := IF(
    @idx_user = 0,
    'CREATE INDEX `idx_order_username` ON `ck_shop_order` (`username`)',
    'SELECT 1'
);

PREPARE stmt_user FROM @ddl_user;
EXECUTE stmt_user;
DEALLOCATE PREPARE stmt_user;

SET @idx_phone := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'ck_shop_order'
      AND index_name = 'idx_order_receiver_phone'
);

SET @ddl_phone := IF(
    @idx_phone = 0,
    'CREATE INDEX `idx_order_receiver_phone` ON `ck_shop_order` (`receiver_phone`)',
    'SELECT 1'
);

PREPARE stmt_phone FROM @ddl_phone;
EXECUTE stmt_phone;
DEALLOCATE PREPARE stmt_phone;
