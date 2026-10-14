package org.jeecg.modules.shop.favorite.service;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 收藏操作的请求上下文（用户 + 店铺）
 * </p>
 *
 * 与 {@code CartContext} 同一套理由（刻意各模块一份，而不是共用一个"大上下文"：
 * 收藏与购物车将来各自的字段会分化，共用会逼两边互相迁就）：
 * <ol>
 *   <li><b>Service 层不碰 HttpServletRequest</b>：店铺解析（StoreResolver）与登录态读取
 *       （SecurityUtils）都留在 Controller，Service 只接收已经解析好的事实；</li>
 *   <li><b>避免"参数漏传就退化成不过滤"</b>：多店铺最危险的事故就是漏了店铺条件，
 *       把 userId + storeId 绑成一个对象传，比 4 个散落的 String 参数更难漏。</li>
 * </ol>
 *
 * 注意 {@code storeOrgCode} 允许为空（店铺没绑部门）：此时**写接口**直接拒绝，
 * **读接口**降级为空列表——两种情况都比"静默按全量处理"安全。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@Accessors(chain = true)
public class FavoriteContext implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 用户ID（sys_user.id） */
    private String userId;

    /** 用户账号（用于冗余记录与审计） */
    private String username;

    /** 店铺ID（ck_shop_store.id） */
    private String storeId;

    /** 店铺名称（仅日志/统计展示用） */
    private String storeName;

    /** 店铺绑定部门的 org_code；为空表示店铺没绑部门 */
    private String storeOrgCode;
}
