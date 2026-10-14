package org.jeecg.modules.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.order.entity.ShopPromotionUserQuota;

/**
 * <p>
 * 促销用户额度 Mapper —— 限购防绕过的核心。
 * </p>
 *
 * <h3>两步式用法（调用顺序不可颠倒）</h3>
 * <ol>
 *   <li>{@link #seedQuota}：确保 {@code (promotion_id, user_id)} 这一行存在。
 *       用 {@code INSERT IGNORE}，并发时只有一个请求真正插入，其余无害跳过。</li>
 *   <li>{@link #tryAccumulate}：带条件的原子累加。影响 0 行 = 超限。</li>
 * </ol>
 *
 * <h3>为什么不能合并成一条 INSERT ... ON DUPLICATE KEY UPDATE</h3>
 * 因为 MySQL 驱动默认返回的是 <b>found rows</b> 而不是 <b>affected rows</b>，
 * 当 {@code ON DUPLICATE KEY UPDATE} 里的条件不成立、数值未变化时，
 * 返回值是 0 还是 1 取决于驱动配置——把"是否超限"的判断寄托在这个行为上，
 * 一旦有人调了连接参数就会静默失效（超限判断永远通过）。
 * 拆成两条语句后，第二步的 UPDATE 返回 0 的含义是明确的。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Mapper
public interface ShopPromotionUserQuotaMapper extends BaseMapper<ShopPromotionUserQuota> {

    /**
     * 确保额度行存在（幂等）。
     *
     * @return 1 = 本次新建；0 = 已存在（不是错误）
     */
    int seedQuota(@Param("id") String id,
                  @Param("promotionId") String promotionId,
                  @Param("userId") String userId,
                  @Param("storeId") String storeId);

    /**
     * 带条件地累加已用数量。
     *
     * @return 1 = 占用成功；0 = 超过限购上限（调用方必须拒绝下单）
     */
    int tryAccumulate(@Param("promotionId") String promotionId,
                      @Param("userId") String userId,
                      @Param("quantity") Integer quantity,
                      @Param("limit") Integer limit);
}
