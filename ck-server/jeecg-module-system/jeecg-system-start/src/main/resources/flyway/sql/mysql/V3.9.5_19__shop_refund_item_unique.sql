-- =============================================================================
-- 退款明细唯一键：(refund_id, order_item_id)
-- 版本：3.9.5.19
--
-- 背景：
--   退款申请按 orderItemId 逐行校验"可退数量"，但同一 orderItemId 在请求里出现两次时，
--   remain 不累计本次请求前面的行，导致两行都能通过校验、refund_quantity 被累加超购买数量、
--   库存按重复数量回补（清单 B13）。
--   代码层已按 orderItemId 合并请求并加 CAS，这里用唯一键做最后一道数据约束：
--   同一退款单内同一个订单明细只允许出现一行。
--
-- 为什么写成"先查 information_schema 再决定是否执行"：
--   MySQL 8 不支持 ADD UNIQUE KEY IF NOT EXISTS。而这个索引在部分环境
--   （包括本仓库的开发库）曾经被**手工**加过一次 —— 如果脚本直接 ALTER，
--   Flyway 会以 "Duplicate key name" 失败，把整个迁移卡在 failed 状态（清单 B16 实测）。
--   做成幂等后，无论索引是否已存在，迁移都能正常完成。
-- -----------------------------------------------------------------------------

SET @idx_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'ck_shop_order_refund_item'
      AND index_name = 'uk_refund_item_order_item'
);

SET @ddl := IF(
    @idx_exists = 0,
    'ALTER TABLE `ck_shop_order_refund_item` ADD UNIQUE KEY `uk_refund_item_order_item` (`refund_id`, `order_item_id`)',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
