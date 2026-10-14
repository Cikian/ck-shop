package org.jeecg.modules.shop.promotion.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 冲突检测的**内部投影行**（不是对外契约，对外统一由 {@code PromotionConflictVO} 承载）
 * </p>
 *
 * 为什么单独建这个类：MyBatis 的聚合查询返回 {@code Map} 时，key 大小写/别名形态
 * 依赖驱动与配置，用强类型投影对象可以让编译期就发现字段名写错，
 * 而不是运行时才 {@code NullPointerException}。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@Schema(description = "冲突检测内部投影行")
public class PromotionConflictRow implements Serializable {
    private static final long serialVersionUID = 1L;

    private String promotionId;

    private String promotionCode;

    private String promotionTitle;

    private String type;

    private Date startTime;

    private Date endTime;

    private String productId;

    private String storeId;
}
