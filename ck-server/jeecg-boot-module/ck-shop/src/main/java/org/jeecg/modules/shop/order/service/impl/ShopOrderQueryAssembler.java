package org.jeecg.modules.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.shop.order.entity.ShopOrder;
import org.jeecg.modules.shop.order.entity.dto.OrderQuery;
import org.jeecg.modules.shop.order.util.OrderTimeRange;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 后台/店铺端订单列表的**查询条件装配**。
 * </p>
 *
 * <p>从 1700 行的 {@code ShopOrderServiceImpl} 里拆出的第一块（清单 B15）：
 * 关键字、状态、渠道、时间区间这些"把 DTO 翻译成 Wrapper"的逻辑没有副作用，
 * 也不需要事务，放在 Service 里只会让主类越来越难读。
 *
 * <p>拆出来后主类只保留"写模型 / 状态机"相关的职责；查询口径集中在这里，
 * 店铺端与后台共用同一份实现（{@code admin} 参数只控制店铺筛选是否生效）。
 */
@Component
public class ShopOrderQueryAssembler {

    /**
     * 关键字是否"看起来像订单号"：SO 开头 + 10~32 位大写字母/数字。
     * 只有这种输入才走 order_no 等值匹配，普通用户名/姓名不会被误判。
     */
    private boolean looksLikeOrderNo(String keyword) {
        String upper = keyword.toUpperCase();
        if (!upper.startsWith("SO") || upper.length() < 10 || upper.length() > 32) {
            return false;
        }
        return upper.chars().allMatch(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9'));
    }
    public void applyCommonFilters(LambdaQueryWrapper<ShopOrder> wrapper, OrderQuery query,
                                    boolean admin) {
        if (StringUtils.isNotBlank(query.getOrderNo())) {
            wrapper.eq(ShopOrder::getOrderNo, query.getOrderNo().trim());
        }
        if (StringUtils.isNotBlank(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            /*
             * 一个输入框搜：订单号 / 用户账号 / 收货人 / 手机号。
             *
             * 旧实现是 4 个 LIKE '%kw%' OR：username/receiver_name/receiver_phone 都没有索引，
             * 订单号的唯一索引也会因为前导 % 失效 —— 一次搜索就是全表扫描。
             * 现在按"关键字像什么"分流：
             *   · 像订单号（SO + 数字/字母，长度 ≥ 10）→ 走 order_no 等值（唯一索引）；
             *   · 其它 → 账号/手机号等值 + 收货人前缀匹配（都能用上索引），不再全模糊。
             * 同时限制关键字长度，超长直接判空（避免恶意的超长 LIKE）。
             */
            if (keyword.length() > 50) {
                wrapper.apply("1 = 0");
            } else if (looksLikeOrderNo(keyword)) {
                wrapper.eq(ShopOrder::getOrderNo, keyword.toUpperCase());
            } else {
                wrapper.and(w -> w.eq(ShopOrder::getUsername, keyword)
                        .or().eq(ShopOrder::getReceiverPhone, keyword)
                        .or().likeRight(ShopOrder::getReceiverName, keyword));
            }
        }
        if (StringUtils.isNotBlank(query.getOrderStatus())) {
            wrapper.eq(ShopOrder::getOrderStatus, query.getOrderStatus().trim().toUpperCase());
        }
        if (StringUtils.isNotBlank(query.getPayStatus())) {
            wrapper.eq(ShopOrder::getPayStatus, query.getPayStatus().trim().toUpperCase());
        }
        if (StringUtils.isNotBlank(query.getRefundStatus())) {
            wrapper.eq(ShopOrder::getRefundStatus, query.getRefundStatus().trim().toUpperCase());
        }
        if (StringUtils.isNotBlank(query.getPayChannel())) {
            wrapper.eq(ShopOrder::getPayChannel, query.getPayChannel().trim().toUpperCase());
        }
        if (StringUtils.isNotBlank(query.getOrderSource())) {
            wrapper.eq(ShopOrder::getOrderSource, query.getOrderSource().trim().toLowerCase());
        }
        if (admin && StringUtils.isNotBlank(query.getStoreId())) {
            wrapper.eq(ShopOrder::getStoreId, query.getStoreId().trim());
        }
        // 时间区间一律左闭右开，且结束时间按用户给定精度**包含**
        // （QueryDateUtils 把 "2026-10-14" 解析成 "2026-10-15 00:00:00"，
        //  否则选"到 10-14"会把当天 00:00 之后的订单全漏掉）
        OrderTimeRange range = OrderTimeRange.parse(query);
        if (range.getStart() != null) {
            wrapper.ge(ShopOrder::getCreateTime, range.getStart());
        }
        if (range.getEnd() != null) {
            wrapper.lt(ShopOrder::getCreateTime, range.getEnd());
        }
        if (range.getPayStart() != null) {
            wrapper.ge(ShopOrder::getPayTime, range.getPayStart());
        }
        if (range.getPayEnd() != null) {
            wrapper.lt(ShopOrder::getPayTime, range.getPayEnd());
        }
        if (query.getMinAmount() != null) {
            wrapper.ge(ShopOrder::getPayAmount, query.getMinAmount());
        }
        if (query.getMaxAmount() != null) {
            wrapper.le(ShopOrder::getPayAmount, query.getMaxAmount());
        }
        if (admin) {
            // 归档订单默认不出现在列表；显式勾选才查。
            // 不提供"永久看不到归档订单"的行为——财务年底查三年前的订单查不到，
            // 第一反应会是"系统把数据弄丢了"
            if (!Boolean.TRUE.equals(query.getIncludeArchived())) {
                wrapper.eq(ShopOrder::getDelFlag, 0);
            }
        }
    }
}
