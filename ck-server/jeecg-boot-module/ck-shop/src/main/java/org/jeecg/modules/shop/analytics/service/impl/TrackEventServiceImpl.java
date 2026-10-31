package org.jeecg.modules.shop.analytics.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.shop.analytics.config.AnalyticsProperties;
import org.jeecg.modules.shop.analytics.entity.TrackEvent;
import org.jeecg.modules.shop.analytics.entity.dto.TrackEventDTO;
import org.jeecg.modules.shop.analytics.geo.GeoResolver;
import org.jeecg.modules.shop.analytics.mapper.TrackEventMapper;
import org.jeecg.modules.shop.analytics.service.ITrackEventService;
import org.jeecg.modules.shop.analytics.service.TrackRedisService;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.jeecg.modules.shop.store.mapper.ShopStoreMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 用户行为明细 Service 实现。
 * </p>
 *
 * <p><b>采集写入绝不阻塞请求线程</b>：{@code enqueue} 只做转换 + 入队（队列满则静默丢弃），
 * 落库由单条后台线程批量 flush。flush 失败/超时**静默丢弃**——丢行为数据，
 * 绝不丢业务、绝不重试风暴（与契约 §5.3 / §9.2 一致）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Slf4j
@Service
public class TrackEventServiceImpl extends ServiceImpl<TrackEventMapper, TrackEvent>
        implements ITrackEventService {

    private final AnalyticsProperties properties;
    private final ShopStoreMapper shopStoreMapper;
    private final ShopOrgScopeResolver orgScopeResolver;
    private final GeoResolver geoResolver;
    private final TrackRedisService trackRedisService;

    private final BlockingQueue<TrackEvent> queue;
    private volatile boolean running = false;
    private Thread flushThread;

    public TrackEventServiceImpl(AnalyticsProperties properties,
                                 ShopStoreMapper shopStoreMapper,
                                 ShopOrgScopeResolver orgScopeResolver,
                                 GeoResolver geoResolver,
                                 TrackRedisService trackRedisService) {
        this.properties = properties;
        this.shopStoreMapper = shopStoreMapper;
        this.orgScopeResolver = orgScopeResolver;
        this.geoResolver = geoResolver;
        this.trackRedisService = trackRedisService;
        this.queue = new LinkedBlockingQueue<>(properties.getQueueCapacity());
    }

    @PostConstruct
    public void start() {
        running = true;
        flushThread = new Thread(this::flushLoop, "shop-analytics-track-flusher");
        flushThread.setDaemon(true);
        flushThread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (flushThread != null) {
            flushThread.interrupt();
        }
        // 停机前尽量把队列里剩余的事件刷掉
        List<TrackEvent> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            flush(remaining);
        }
    }

    @Override
    public void enqueue(List<TrackEventDTO> events, String storeCode, String clientIp) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (TrackEventDTO dto : events) {
            if (dto == null || StringUtils.isBlank(dto.getEventName())) {
                continue;
            }
            TrackEvent event = convert(dto, storeCode);
            // 实时层：先写 Redis（秒级在线/PV/UV），再入队落库
            trackRedisService.recordOnline(event.getSessionId());
            trackRedisService.recordPv(event.getEventTime());
            trackRedisService.recordUv(event.getVisitorId(), event.getEventTime());
            // 入队失败（队列满）即静默丢弃
            if (!queue.offer(event)) {
                log.debug("[analytics] 采集队列已满，静默丢弃事件：{}", event.getEventName());
            }
        }
    }

    private TrackEvent convert(TrackEventDTO dto, String storeCode) {
        LocalDateTime eventTime = dto.getEventTime() == null ? LocalDateTime.now()
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(dto.getEventTime()), ZoneId.systemDefault());

        resolveStore(dto, storeCode);
        resolveGeo(dto);

        TrackEvent e = new TrackEvent();
        e.setId(IdWorker.getIdStr());
        e.setEventTime(eventTime);
        e.setEventName(trim(dto.getEventName(), 64));
        e.setSessionId(trim(dto.getSessionId(), 64));
        e.setVisitorId(trim(dto.getVisitorId(), 64));
        e.setUserId(trim(dto.getUserId(), 36));
        e.setOrgCode(trim(dto.getOrgCode(), 64));
        e.setStoreId(trim(dto.getStoreId(), 36));
        e.setProductId(trim(dto.getProductId(), 36));
        e.setPageUrl(trim(dto.getPageUrl(), 512));
        e.setPagePath(trim(dto.getPagePath(), 256));
        e.setReferrer(trim(dto.getReferrer(), 512));
        e.setUtmSource(trim(dto.getUtmSource(), 128));
        e.setUtmMedium(trim(dto.getUtmMedium(), 128));
        e.setUtmCampaign(trim(dto.getUtmCampaign(), 128));
        e.setDeviceType(trim(dto.getDeviceType(), 16));
        e.setOs(trim(dto.getOs(), 64));
        e.setBrowser(trim(dto.getBrowser(), 64));
        e.setScreen(trim(dto.getScreen(), 32));
        e.setLang(trim(dto.getLang(), 16));
        e.setCountryCode(trim(dto.getCountryCode(), 8));
        e.setCountryName(trim(dto.getCountryName(), 64));
        e.setProvinceName(trim(dto.getProvinceName(), 64));
        e.setCityName(trim(dto.getCityName(), 64));
        e.setProvinceCode(trim(dto.getProvinceCode(), 16));
        e.setIp(trim(dto.getIp(), 45));
        e.setDurationMs(dto.getDurationMs());
        e.setProps(serializeProps(dto.getProps()));
        return e;
    }

    /** 店铺快照解析：BFF 传 X-Store-Code，据此补 org_code/store_id；解析不到留空不报错 */
    private void resolveStore(TrackEventDTO dto, String storeCode) {
        if (StringUtils.isNotBlank(dto.getOrgCode()) && StringUtils.isNotBlank(dto.getStoreId())) {
            return;
        }
        if (StringUtils.isBlank(storeCode)) {
            return;
        }
        try {
            ShopStore store = shopStoreMapper.selectEnabledByCode(storeCode);
            if (store == null) {
                return;
            }
            if (StringUtils.isBlank(dto.getStoreId())) {
                dto.setStoreId(store.getId());
            }
            if (StringUtils.isBlank(dto.getOrgCode())) {
                dto.setOrgCode(orgScopeResolver.resolveStoreOrgCodeOrNull(store));
            }
        } catch (Exception ignore) {
            // 店铺解析失败不影响采集：org_code/store_id 留空
        }
    }

    /** 地域解析：BFF 不传 country/province/city，Java 端据 ip 用 GeoLite2 填充 */
    private void resolveGeo(TrackEventDTO dto) {
        if (StringUtils.isNotBlank(dto.getCountryCode())) {
            return;
        }
        if (StringUtils.isBlank(dto.getIp())) {
            return;
        }
        GeoResolver.GeoResult geo = geoResolver.resolve(dto.getIp());
        if (geo == null) {
            return;
        }
        dto.setCountryCode(geo.getCountryCode());
        dto.setCountryName(geo.getCountryName());
        dto.setProvinceCode(geo.getProvinceCode());
        dto.setProvinceName(geo.getProvinceName());
        dto.setCityName(geo.getCityName());
    }

    /** props 序列化为 JSON 字符串；空时置 NULL（MySQL json 列不接受空串） */
    private String serializeProps(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return null;
        }
        try {
            return JSON.toJSONString(props);
        } catch (Exception e) {
            log.debug("[analytics] props 序列化失败，置 NULL：{}", e.getMessage());
            return null;
        }
    }

    private void flushLoop() {
        while (running) {
            List<TrackEvent> batch = new ArrayList<>(properties.getBatchSize());
            try {
                TrackEvent first = queue.poll(properties.getFlushIntervalMs(), TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, properties.getBatchSize() - 1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (!batch.isEmpty()) {
                flush(batch);
            }
        }
    }

    private void flush(List<TrackEvent> batch) {
        try {
            baseMapper.batchInsert(batch);
        } catch (Exception e) {
            // 落库失败静默丢弃：行为数据可丢，绝不能因为重试把库打挂
            log.warn("[analytics] 行为明细批量写入失败，静默丢弃 {} 条：{}", batch.size(), e.getMessage());
        }
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
