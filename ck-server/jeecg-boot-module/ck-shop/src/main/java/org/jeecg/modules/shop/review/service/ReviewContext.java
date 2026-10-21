package org.jeecg.modules.shop.review.service;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 评价相关请求的上下文（当前登录人 + 当前店铺）。
 * </p>
 *
 * 与 {@code FavoriteContext} / {@code CartContext} 同一套理由，刻意各模块一份：
 * <ol>
 *   <li><b>Service 层不碰 HttpServletRequest</b>：店铺解析与登录态读取留在 Controller；</li>
 *   <li><b>userId + storeId 绑成对象传</b>：多店铺最危险的事故是"漏传店铺条件"，
 *       一个对象比四个散落的 String 更难漏。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

@Data
@Accessors(chain = true)
public class ReviewContext implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 用户ID（sys_user.id） */
    private String userId;

    /** 用户账号（用于冗余记录与审计） */
    private String username;

    /** 店铺ID（ck_shop_store.id） */
    private String storeId;

    /** 店铺名称（仅日志展示用） */
    private String storeName;

    /** 店铺绑定部门的 org_code；为空表示店铺没绑部门 */
    private String storeOrgCode;
}
