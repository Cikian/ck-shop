package org.jeecg.modules.shop.promotion.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 促销活动列表查询条件
 * </p>
 *
 * 显式声明筛选字段，避免把 pageNo / pageSize 之类非实体字段混进条件拼装里
 * （与 {@code ProductQuery} 保持同一风格）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@Schema(description = "促销活动列表查询条件")
public class PromotionQuery implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 店铺筛选。语义上**不是**"再加一个 where 条件"，而是与当前账号可见域取交集：
     * 为空时按账号可见域过滤；填了但不在可见域内，一律查不到数据（绝不退化成不过滤）。
     */
    @Schema(description = "店铺ID, 可空; 为空时按当前登录人的数据权限过滤")
    private String storeId;

    @Schema(description = "活动类型: DIRECT-直降 / PERCENT-折扣 / FULL_CUT-满减 / SECKILL-秒杀")
    private String type;

    @Schema(description = "状态: Y-启用, N-停用")
    private String status;

    @Schema(description = "活动编码，模糊匹配")
    private String code;

    /** 同时匹配翻译表与主表的 title（zh 翻译是列表展示用的那一份） */
    @Schema(description = "活动名称，模糊匹配(主表与翻译表)")
    private String title;

    @Schema(description = "开始时间范围-起, yyyy-MM-dd HH:mm:ss")
    private String startTimeBegin;

    @Schema(description = "开始时间范围-止, yyyy-MM-dd HH:mm:ss")
    private String startTimeEnd;

    @Schema(description = "结束时间范围-起, yyyy-MM-dd HH:mm:ss")
    private String endTimeBegin;

    @Schema(description = "结束时间范围-止, yyyy-MM-dd HH:mm:ss")
    private String endTimeEnd;
}
