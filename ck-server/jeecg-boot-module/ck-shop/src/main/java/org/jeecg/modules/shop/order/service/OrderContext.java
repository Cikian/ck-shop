package org.jeecg.modules.shop.order.service;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 订单操作的上下文：当前登录人 + 当前店铺 + 店铺部门编码。
 * </p>
 *
 * 与购物车模块的 {@code CartContext} 结构相同但**独立定义**：
 * 两个模块的上下文未来会分化（订单还需要下单 IP、UA、来源等），
 * 共用一个类会让"改订单的字段"波及购物车。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
public class OrderContext implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当前登录用户ID */
    private String userId;

    /** 当前登录账号 */
    private String username;

    /** 用户昵称（可选，冗余到订单便于后台展示） */
    private String realName;

    /** 当前店铺ID */
    private String storeId;

    /** 当前店铺名称（下单时快照） */
    private String storeName;

    /**
     * 当前店铺绑定的部门编码。
     *
     * <p>订单的**数据权限隔离维度**，也是所有后台统计的过滤依据。
     * 为空时下单必须被拒绝——写一个空值进库，这条订单在后台任何店铺筛选下都查不到，
     * 等于制造了一张"谁也看不到的订单"。
     */
    private String storeOrgCode;

    /** 下单 IP（风控与异常排查） */
    private String clientIp;

    /** User-Agent（风控与异常排查） */
    private String userAgent;

    /** 下单来源：web/h5/app/api */
    private String orderSource;

    /** 渠道/推广位编码 */
    private String channelCode;

    /** UTM 来源 */
    private String utmSource;

    /** 来源页面 */
    private String referrer;
}
