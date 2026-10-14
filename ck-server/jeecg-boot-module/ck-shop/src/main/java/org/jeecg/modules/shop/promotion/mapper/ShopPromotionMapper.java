package org.jeecg.modules.shop.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.promotion.entity.dto.PromotionConflictRow;
import org.jeecg.modules.shop.promotion.entity.ShopPromotion;
import org.jeecg.modules.shop.promotion.entity.ShopPromotionProduct;
import org.jeecg.modules.shop.product.entity.ShopProductTranslation;
import org.jeecg.modules.shop.store.entity.ShopStore;

import java.util.Date;
import java.util.List;

/**
 * <p>
 * 促销活动 Mapper
 * </p>
 *
 * <p>"进行中的活动"用 QueryWrapper 即可表达（等值 + 时间区间 + 排序 + LIMIT），
 * 因此不额外写 XML：SQL 越少，漏店铺条件的口子就越少。
 * 只有两条确实用 wrapper 写不出来的查询落在这里（店铺名映射、冲突检测）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-13
 */

@Mapper
public interface ShopPromotionMapper extends BaseMapper<ShopPromotion> {

    /**
     * 店铺 id → 店铺名（列表/冲突结果 join 出 storeName）。
     *
     * <p>为什么不写成 join 的分页 SQL：列表条件（可见域、时间区间、标题模糊匹配翻译表）
     * 已经用 wrapper 表达得很完整，改成自定义分页 SQL 就得把那套条件翻译一遍，
     * 两份实现必然分叉。店铺是量级很小的维度，按 id 批量取名字更稳。
     *
     * @param storeIds 店铺 id 列表，**不可为空**（调用方需自行判空）
     */
    @Select("<script>SELECT id, name FROM ck_shop_store WHERE id IN "
            + "<foreach collection='storeIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach>"
            + "</script>")
    List<ShopStore> selectStoreNames(@Param("storeIds") List<String> storeIds);

    /**
     * 商品 id → 商品名（冲突提示用），只取 zh 翻译行。
     *
     * <p>复用 {@code ShopProductTranslation} 实体映射：字段看着多，MyBatis 只按 select 出来的列赋值，
     * 不必为此再造一个投影对象（投影对象越多，字段改名时越容易漏）。
     * 没有翻译行的商品由服务层回退成 {@code spu_code}。
     */
    @Select("<script>SELECT product_id AS productId, language_code AS languageCode, title "
            + "FROM ck_shop_product_translation WHERE language_code = 'zh' AND product_id IN "
            + "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</script>")
    List<ShopProductTranslation> selectProductTitles(@Param("productIds") List<String> productIds);

    /**
     * 统计活动编码占用（**包含软删除行**）。
     *
     * <p>为什么要绕过 {@code @TableLogic}：编码与 {@code spu_code} 同理会被写进前台站点配置，
     * 删了再建同码活动会让历史配置指向另一个活动；恢复数据时也会直接撞唯一索引。
     * {@code BaseMapper.count} 会被自动追加 {@code del_flag = 0}，只能显式写 SQL。
     *
     * @param excludeId 可空：编辑时要排除自己
     */
    @Select("<script>SELECT COUNT(*) FROM ck_shop_promotion "
            + "WHERE store_id = #{storeId} AND code = #{code} "
            + "<if test='excludeId != null and excludeId != \"\"'> AND id != #{excludeId} </if>"
            + "</script>")
    long countByCodeIncludingDeleted(@Param("code") String code,
                                     @Param("storeId") String storeId,
                                     @Param("excludeId") String excludeId);

    /**
     * 冲突检测：这些商品在 [startTime, endTime] 内是否已被**其它活动**占用。
     *
     * <p>重叠判定用闭区间：{@code 已有.start <= 本次.end AND 已有.end >= 本次.start}。
     * 边界相接（前一个 10:00 结束、后一个 10:00 开始）也算重叠——运营眼里那就是同一时刻切换，
     * 前台价格与倒计时会在同一秒打架，宁可提示出来让人主动错开。
     *
     * <p>只取 {@code del_flag = 0}（已删除的活动不应再占位）；
     * {@code excludeId} 传编辑中的活动 id，否则保存时会"自己跟自己冲突"。
     * 条件里没有任何字符串拼接，商品/时间全部走 {@code #} 预编译参数。
     */
    @Select("<script>"
            + "SELECT p.id AS promotionId, p.code AS promotionCode, p.title AS promotionTitle, p.type AS type, "
            + "       p.start_time AS startTime, p.end_time AS endTime, pp.product_id AS productId, p.store_id AS storeId "
            + "FROM ck_shop_promotion p "
            + "INNER JOIN ck_shop_promotion_product pp ON pp.promotion_id = p.id "
            + "WHERE p.del_flag = 0 "
            + "  AND pp.product_id IN "
            + "  <foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach> "
            + "  AND p.start_time &lt;= #{endTime} AND p.end_time &gt;= #{startTime} "
            + "  <if test='excludeId != null and excludeId != \"\"'> AND p.id != #{excludeId} </if> "
            + "ORDER BY p.start_time ASC, p.id ASC, pp.product_id ASC"
            + "</script>")
    List<PromotionConflictRow> selectConflicts(@Param("productIds") List<String> productIds,
                                              @Param("startTime") Date startTime,
                                              @Param("endTime") Date endTime,
                                              @Param("excludeId") String excludeId);

    /**
     * 取当前**正在进行中**、且作用于给定商品集合的促销活动。
     *
     * <p>供下单算价使用（{@code OrderAmountCalculator}）。三个条件缺一不可：
     * <ul>
     *   <li>{@code status = 'Y'}：停用的活动不能算价；</li>
     *   <li>{@code start_time <= now <= end_time}：用数据库时间语义的闭区间；
     *       "活动刚结束但前端页面还没刷新"是最常见的价格纠纷来源，
     *       算价必须由服务端按下单这一刻重新判定；</li>
     *   <li>{@code store_id}：促销按店铺隔离，漏掉它就是 A 店的优惠打到 B 店的单上。</li>
     * </ul>
     *
     * <p>排序 {@code sort_order DESC, id ASC}：同一个商品只命中一个活动（不叠加），
     * 由调用方取第一条。之所以不叠加：现有系统在保存活动时就做了冲突检测
     * （{@code selectConflicts}），同一商品同一时间本就不该有两个活动；
     * 真出现了重叠（历史脏数据），取优先级最高的一条比"叠加"更可预测——
     * 叠加会产生运营无法手工复算的金额。
     *
     * @param storeId    店铺ID
     * @param productIds 商品ID集合，**不可为空**
     * @param now        判定时刻
     */
    @Select("<script>"
            + "SELECT DISTINCT p.* FROM ck_shop_promotion p "
            + "INNER JOIN ck_shop_promotion_product pp ON pp.promotion_id = p.id "
            + "WHERE p.del_flag = 0 AND p.status = 'Y' AND p.store_id = #{storeId} "
            + "  AND p.start_time &lt;= #{now} AND p.end_time &gt;= #{now} "
            + "  AND pp.product_id IN "
            + "  <foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach> "
            + "ORDER BY p.sort_order DESC, p.id ASC"
            + "</script>")
    List<ShopPromotion> selectActiveByProducts(@Param("storeId") String storeId,
                                              @Param("productIds") List<String> productIds,
                                              @Param("now") Date now);

    /**
     * 取这些活动关联的商品/SKU 配置行（用于判定活动作用于整个 SPU 还是某个 SKU）。
     *
     * @param promotionIds 活动ID集合，**不可为空**
     */
    @Select("<script>SELECT * FROM ck_shop_promotion_product WHERE promotion_id IN "
            + "<foreach collection='promotionIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</script>")
    List<ShopPromotionProduct> selectProductsByPromotionIds(@Param("promotionIds") List<String> promotionIds);
}
