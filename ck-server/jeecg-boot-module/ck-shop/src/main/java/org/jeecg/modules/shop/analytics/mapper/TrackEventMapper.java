package org.jeecg.modules.shop.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.analytics.entity.TrackEvent;

import java.util.List;

/**
 * <p>
 * 用户行为明细 Mapper。
 * </p>
 *
 * <p>明细表是 append-only 账本，这里只提供批量写入与分页查询；不提供物理删除。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Mapper
public interface TrackEventMapper extends BaseMapper<TrackEvent> {

    /**
     * JDBC 批量插入（单条 INSERT 多 VALUES）。
     * 采集链路异步批量写入，绝不阻塞请求线程；批量失败由调用方静默丢弃。
     */
    int batchInsert(@Param("list") List<TrackEvent> list);
}
