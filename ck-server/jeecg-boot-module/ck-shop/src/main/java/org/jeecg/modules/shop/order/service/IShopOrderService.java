package org.jeecg.modules.shop.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.order.entity.ShopOrder;
import org.jeecg.modules.shop.order.entity.dto.OrderPreviewDTO;
import org.jeecg.modules.shop.order.entity.dto.OrderQuery;
import org.jeecg.modules.shop.order.entity.dto.OrderShipDTO;
import org.jeecg.modules.shop.order.entity.dto.OrderSubmitDTO;
import org.jeecg.modules.shop.order.entity.vo.AdminOrderDetailVO;
import org.jeecg.modules.shop.order.entity.vo.AdminOrderListVO;
import org.jeecg.modules.shop.order.entity.vo.OrderPreviewVO;
import org.jeecg.modules.shop.order.entity.vo.OrderSubmitVO;
import org.jeecg.modules.shop.order.entity.vo.StoreOrderDetailVO;
import org.jeecg.modules.shop.order.entity.vo.StoreOrderListVO;

/**
 * <p>
 * 订单 Service。
 * </p>
 *
 * <h3>前台与后台的入口刻意分开</h3>
 * 前台的每个方法都带 {@link OrderContext} 且**强制按当前用户过滤**；
 * 后台方法不带上下文，但有 {@code ShopOrgScopeResolver} 的可见域过滤。
 * 混在一起（例如一个 {@code list(query, asAdmin)}）迟早会出现
 * "普通用户传个参数就看到了别人的订单"。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
public interface IShopOrderService extends IService<ShopOrder> {

    // ==================================================================
    // 前台（买家）
    // ==================================================================

    /**
     * 结算预览：服务端算钱 + 签发价格令牌。
     *
     * <p>这个接口是"防意外扣款"的关键——它把服务端算出的每一项金额摊开给用户看，
     * 并回一个绑定了金额与明细的令牌，提交订单时再复核一遍。
     */
    OrderPreviewVO preview(OrderContext context, OrderPreviewDTO dto);

    /**
     * 提交订单。
     *
     * <h3>执行的顺序（三步都不可省）</h3>
     * <ol>
     *   <li><b>幂等前置</b>：先按幂等键查一次，命中就直接返回那笔订单，不做任何写操作；</li>
     *   <li><b>验价 + 重算</b>：校验价格令牌，并用服务端重算结果与令牌里的金额比对，
     *       不一致就拒绝（促销结束/商品调价），要求用户回结算页重新确认；</li>
     *   <li><b>写单 + 预占</b>：创建订单与明细快照、预占库存、写操作流水，
     *       全部在**同一个事务**里。任何一步失败整单回滚，不会留下"占了库存没有订单"的孤儿数据。</li>
     * </ol>
     *
     * <p>数据库对 {@code idempotent_key} 有唯一索引做最后兜底：并发重复提交时
     * 后一个请求会撞唯一键，服务端捕获后返回已存在的那笔订单。
     */
    OrderSubmitVO submit(OrderContext context, OrderSubmitDTO dto);

    /** 我的订单列表 */
    Page<StoreOrderListVO> storePage(OrderContext context, OrderQuery query);

    /**
     * 我的订单详情（含地址、支付单、退款单与买家可见时间线）。
     *
     * @param locale 访客 locale（zh/en/fr/zh-TW…），用于把地址快照里的当地文字
     *               现算成访客语言的整串（{@code receiverFullAddressLocalized}）。
     *               服务层刻意不读请求上下文：单测与后台脚本都要能复用，语言只能由调用方显式给。
     */
    StoreOrderDetailVO storeDetail(OrderContext context, String orderNo, String locale);

    /**
     * 买家取消订单（仅待支付可取消）。
     *
     * <p>已支付订单**不允许买家自助取消**：钱已经收了，
     * 直接取消会产生"订单关了钱没退"的资金事故。
     * 已付款的诉求应走退款流程（申请 → 审核 → 退款）。
     */
    void cancelByBuyer(OrderContext context, String orderNo, String reason);

    /** 买家确认收货 */
    void confirmReceive(OrderContext context, String orderNo);

    // ==================================================================
    // 后台（管理员）
    // ==================================================================

    Page<AdminOrderListVO> adminPage(OrderQuery query);

    AdminOrderDetailVO adminDetail(String orderNo);

    /**
     * 后台发货。
     *
     * <p>只允许 {@code WAIT_SHIP → SHIPPED}。用 CAS 更新，
     * 两个客服同时点发货只有一次会成功——重复发货是真实存在的运营事故
     * （同一批货发两次，运费与库存双损失）。
     */
    void ship(String orderNo, OrderShipDTO dto);

    /**
     * 后台关闭/取消订单。
     *
     * <p>分两种情况，前置条件**必须由服务端强制校验**：
     * <ul>
     *   <li>待支付：直接关闭并释放库存预占；</li>
     *   <li>已支付（待发货/已发货）：**必须先完成全额退款**，
     *       否则拒绝。这是"订单关了钱没退"这类资金事故的唯一防线。</li>
     * </ul>
     */
    void adminClose(String orderNo, String reason);

    /** 修改卖家备注（会写操作流水） */
    void updateSellerRemark(String orderNo, String remark);

    /**
     * 归档订单。
     *
     * <p>仅允许已取消/已关闭的订单归档；有效订单（待支付/待发货/已发货/已完成）
     * **在逻辑层面也禁止删除**，归档限制写在 SQL 的 WHERE 里，任何调用路径都绕不过。
     */
    void archive(String orderNo);

    /** 按订单号取订单（**不做过滤**，仅供内部与后台使用，带可见域校验由调用方完成） */
    ShopOrder getByOrderNo(String orderNo);

    /**
     * 超时关单（定时任务 / 手动补偿）。
     *
     * <p>关单**之前**会先向渠道确认一次"这笔到底付没付"（四层兜底里的"主动查询"）：
     * 渠道说已成功就转为支付成功，绝不关单。
     *
     * @return true = 本次确实关闭了订单；false = 订单已不是待支付（被支付或被别处关闭）
     */
    boolean closeIfOverdue(ShopOrder order);
}
