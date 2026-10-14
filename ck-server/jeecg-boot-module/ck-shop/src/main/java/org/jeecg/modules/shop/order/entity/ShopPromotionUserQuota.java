package org.jeecg.modules.shop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 促销用户额度（ck_shop_promotion_user_quota）—— 防"限购绕过"
 * </p>
 *
 * <h3>为什么不能靠"查订单求和"来限购</h3>
 * 秒杀活动的"每人限购 2 件"如果实现成：
 * <pre>
 *   int bought = selectSum(quantity) from order_item where promotion_id=? and user_id=?;
 *   if (bought + qty &lt;= limit) { 下单 }
 * </pre>
 * 那么同一用户开两个标签页同时下单，两边都会读到 {@code bought = 0}，
 * 两边都判定"可以买 2 件"，最终买走 4 件。
 * 这就是文档 §3 里的"限购绕过"。
 *
 * <p>正确做法是给每个 {@code (promotionId, userId)} 建一行记录作为**行锁载体**，
 * 唯一的更新语句是：
 * <pre>
 *   UPDATE ck_shop_promotion_user_quota
 *      SET used_quantity = used_quantity + #{qty}
 *    WHERE promotion_id = ? AND user_id = ?
 *      AND used_quantity + #{qty} &lt;= #{limit}
 * </pre>
 * 影响行数为 0 即超限。InnoDB 的行锁会把两个并发请求串行化，
 * 不存在"读-判断-写"的时间窗。
 *
 * <p>额度**只增不减**（取消订单不回退额度）：秒杀是"抢到就占用"的语义，
 * 允许回退会让"先抢满、再取消、再抢"成为绕过手段。
 * 需要人工释放时走后台的额度调整（留 {@code ADJUST} 事件），而不是自动回退。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@TableName("ck_shop_promotion_user_quota")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "促销用户额度")
public class ShopPromotionUserQuota implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "促销活动ID")
    private String promotionId;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "店铺ID")
    private String storeId;

    @Schema(description = "已占用数量")
    private Integer usedQuantity;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建日期")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新日期")
    private Date updateTime;
}
