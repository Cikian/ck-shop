package org.jeecg.modules.shop.product.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.product.entity.vo.ProductStatVO;

import java.util.Date;
import java.util.List;

/**
 * <p>
 * 商品真实统计 Mapper（销量 / 收藏数 / 评价数与评分）
 * </p>
 *
 * 这个 Mapper 只有两个职责，刻意不塞进 {@code ShopProductMapper}：
 * <ol>
 *   <li><b>实时聚合</b>：按真实来源算出"现在到底卖了多少、被收藏多少、几条几分的评价"，
 *       供店铺端详情 / 列表直接展示 —— 展示值永远是聚合值，不是缓存列；</li>
 *   <li><b>回写缓存</b>：把同样的口径写回 {@code ck_shop_product} 的冗余列，
 *       让"按销量排序"这类 SQL 排序仍能走索引（排序用缓存、展示用聚合，两不相干）。</li>
 * </ol>
 *
 * 口径（与建表脚本 / 接口契约保持一致）：
 * <ul>
 *   <li>销量：{@code pay_status='PAID'} 且订单未取消/关闭（全额退款会把 pay_status 置为 REFUNDED，
 *       因此退款单会自动掉出统计，不需要额外减项）；</li>
 *   <li>收藏：{@code del_flag=0} 的关系行数；</li>
 *   <li>评价：{@code status='Y' AND del_flag=0} 的条数与 {@code rating} 平均值。</li>
 * </ul>
 *
 * <b>只有批量方法，没有单商品方法</b>：单商品只是"集合里只有一个元素"的特例，
 * 两套 SQL 迟早写得不一致（详情页和列表页对同一件商品给出不同销量，是最难解释的 bug）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

@Mapper
public interface ShopProductStatMapper {

    /**
     * 批量真实统计（详情页传 1 个 id，列表页传一整页）。
     *
     * @param productIds 商品主键集合，调用方保证非空
     * @param monthSince 近 30 天的起点（由 Java 算好传入，SQL 里不出现 DATE_SUB 等方言函数）
     * @return 与 productIds 同规模的结果集（统计为 0 的商品也会返回一行全 0 记录）
     */
    List<ProductStatVO> selectStatByProductIds(@Param("productIds") List<String> productIds,
                                               @Param("monthSince") Date monthSince);

    /**
     * 把真实统计回写到 {@code ck_shop_product} 的冗余列（评价提交、收藏切换等写路径收尾用）。
     *
     * <p>为什么要有回写：排序（{@code ORDER BY month_sales_count DESC}）与后台统计走的是冗余列，
     * 如果只在读接口里聚合、冗余列永远不动，将来一排序就会把"从未卖出的商品"排到前面。
     *
     * @return 受影响行数（0 = 商品不存在或已删除）
     */
    int refreshProductStats(@Param("productId") String productId,
                            @Param("monthSince") Date monthSince);
}
