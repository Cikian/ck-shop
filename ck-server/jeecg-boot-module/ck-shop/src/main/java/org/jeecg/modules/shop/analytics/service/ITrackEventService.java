package org.jeecg.modules.shop.analytics.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.analytics.entity.TrackEvent;
import org.jeecg.modules.shop.analytics.entity.dto.TrackEventDTO;

import java.util.List;

/**
 * <p>
 * 用户行为明细 Service。
 * </p>
 *
 * <p>采集写入**绝不阻塞请求线程**：入队即返回，由后台单线程批量 flush 落库；
 * 队列满/落库失败一律静默丢弃（丢行为数据，绝不丢业务、绝不重试风暴）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
public interface ITrackEventService extends IService<TrackEvent> {

    /**
     * 批量接收采集事件：转换 + 地域解析 + 店铺解析 + 实时 Redis + 异步入队。
     *
     * @param events    事件列表
     * @param storeCode 请求头 X-Store-Code（可空，用于补 org_code/store_id 快照）
     * @param clientIp  来源 IP（限流维度）
     */
    void enqueue(List<TrackEventDTO> events, String storeCode, String clientIp);
}
