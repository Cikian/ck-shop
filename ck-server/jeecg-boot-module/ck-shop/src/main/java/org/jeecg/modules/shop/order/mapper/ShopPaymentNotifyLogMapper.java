package org.jeecg.modules.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.jeecg.modules.shop.order.entity.ShopPaymentNotifyLog;

/**
 * <p>渠道回调流水 Mapper</p>
 *
 * <b>两阶段写入，不物理删除</b>：
 * <ol>
 *   <li>接收：插入一条 {@code RECEIVED}，唯一键 {@code dedup_key} 负责去重；</li>
 *   <li>处理完：更新为 {@code PROCESSED / DUPLICATE / FAILED}。</li>
 * </ol>
 * 结算失败时还要把 {@code dedup_key} 改成一个"已释放"的独立值（保留原行做审计），
 * 这样渠道下次重推不会被唯一键当成重复回调——详见
 * {@code ShopPaymentServiceImpl#markNotifyFailedAndRelease}。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Mapper
public interface ShopPaymentNotifyLogMapper extends BaseMapper<ShopPaymentNotifyLog> {
}
