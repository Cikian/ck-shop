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
-- 说明：
--   MySQL 8 不支持 ADD UNIQUE KEY IF NOT EXISTS，本脚本由 Flyway 保证只执行一次。
-- -----------------------------------------------------------------------------

ALTER TABLE `ck_shop_order_refund_item`
    ADD UNIQUE KEY `uk_refund_item_order_item` (`refund_id`, `order_item_id`);
