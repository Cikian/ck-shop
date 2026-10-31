package org.jeecg.modules.shop.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.analytics.entity.TrackSummary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 用户行为汇总 Mapper。
 * </p>
 *
 * <p>聚合任务用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 幂等地把明细聚合进汇总表；
 * 查询接口用 {@link #selectByDim} 读回原始汇总行，维度分组/解析/比率计算收口在 Service。
 *
 * <p>注意 {@code orgExpr}/{@code productExpr}/{@code dimValueExpr}/{@code bucketExpr}
 * 是**内部白名单生成的 SQL 片段**（非用户输入），用 {@code ${}} 拼接；其余值为参数绑定。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Mapper
public interface TrackSummaryMapper extends BaseMapper<TrackSummary> {

    /**
     * 把行为明细聚合进汇总表（幂等：按唯一键覆盖，而非累加）。
     *
     * @param pvEvent 该维度"浏览量"对应的事件名（product 维度用 product_view，其余 page_view）
     */
    void aggregateTrack(@Param("dimType") String dimType,
                        @Param("granularity") String granularity,
                        @Param("pvEvent") String pvEvent,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end,
                        @Param("orgExpr") String orgExpr,
                        @Param("productExpr") String productExpr,
                        @Param("dimValueExpr") String dimValueExpr,
                        @Param("bucketExpr") String bucketExpr);

    /** 加购回填（global/product 共用，靠 productExpr/dimValueExpr 区分） */
    void backfillAddCart(@Param("granularity") String granularity,
                         @Param("start") LocalDateTime start,
                         @Param("end") LocalDateTime end,
                         @Param("orgExpr") String orgExpr,
                         @Param("productExpr") String productExpr,
                         @Param("dimType") String dimType,
                         @Param("dimValueExpr") String dimValueExpr,
                         @Param("bucketExpr") String bucketExpr);

    /** 下单回填（global 维度，来自 ck_shop_order 按下单时间） */
    void backfillOrderGlobal(@Param("granularity") String granularity,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end,
                             @Param("orgExpr") String orgExpr,
                             @Param("bucketExpr") String bucketExpr);

    /** 支付回填（global 维度，来自 ck_shop_order 按支付时间） */
    void backfillPaidGlobal(@Param("granularity") String granularity,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end,
                            @Param("orgExpr") String orgExpr,
                            @Param("bucketExpr") String bucketExpr);

    /** 下单回填（product 维度，来自订单明细按下单时间） */
    void backfillOrderProduct(@Param("granularity") String granularity,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end,
                              @Param("orgExpr") String orgExpr,
                              @Param("bucketExpr") String bucketExpr);

    /** 支付回填（product 维度，来自订单明细按支付时间） */
    void backfillPaidProduct(@Param("granularity") String granularity,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end,
                             @Param("orgExpr") String orgExpr,
                             @Param("bucketExpr") String bucketExpr);

    /**
     * 按维度读回汇总行（可选按 dim_value 前缀过滤，用于 device/region 的子维度）。
     *
     * @param allRows true=超管全局视角（org_code='ALL' 行）；false=按 scope 前缀过滤 per-org 行
     */
    List<TrackSummary> selectByDim(@Param("dimType") String dimType,
                                   @Param("granularity") String granularity,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end,
                                   @Param("scope") List<String> scope,
                                   @Param("allRows") boolean allRows,
                                   @Param("dimValuePrefix") String dimValuePrefix);
}
