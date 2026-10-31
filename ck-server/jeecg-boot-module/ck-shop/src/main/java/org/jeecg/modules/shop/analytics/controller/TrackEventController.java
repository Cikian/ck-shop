package org.jeecg.modules.shop.analytics.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.IpUtils;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.shop.analytics.config.AnalyticsProperties;
import org.jeecg.modules.shop.analytics.entity.dto.TrackEventRequest;
import org.jeecg.modules.shop.analytics.service.ITrackEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * 行为采集接收接口（BFF 内部调用）。
 * </p>
 *
 * <h3>安全</h3>
 * 这个接口 {@code @IgnoreAuth}（BFF 服务端调用，不带登录态），因此安全依赖：
 * 简单签名（可选，配置了密钥才校验）+ 单 IP 分钟级限流。
 * 采集写入**绝不阻塞请求线程**：异步入队 + 批量 insert，超时/失败静默丢弃。
 *
 * <h3>@IgnoreAuth 必须写显式 path</h3>
 * JeecgBoot 的 {@code @IgnoreAuth} 由 {@code IgnoreAuthPostProcessor} 扫描方法级 mapping 的
 * {@code value()} 生成免登录 URL；不写 path 时 value() 为空数组，注解形同没加。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Tag(name = "行为采集")
@RestController
@RequestMapping("/shop/analytics/track")
@Slf4j
public class TrackEventController {

    private static final long SIGNATURE_WINDOW_MS = 5 * 60 * 1000L;

    @Autowired
    private ITrackEventService trackEventService;

    @Autowired
    private AnalyticsProperties properties;

    /** 采集签名密钥：复用前后端共享的 jeecg.signatureSecret（= SIGN_SECRET / JEECG_SIGNATURE_SECRET） */
    @Value("${jeecg.signatureSecret:}")
    private String sharedSignatureSecret;

    private final Map<String, Deque<Long>> rateWindow = new ConcurrentHashMap<>();

    /**
     * 批量接收事件：{@code POST /shop/analytics/track/events}，body {@code {"events":[...]}}。
     * 字段 snake_case，event_time 为 epoch 毫秒；country/province/city 由 Java 据 ip 解析。
     */
    @IgnoreAuth
    @Operation(summary = "行为事件批量上报(BFF内部)")
    @PostMapping(value = "/events")
    public Result<String> events(@RequestBody(required = false) TrackEventRequest request,
                                 HttpServletRequest http) {
        String clientIp = resolveIp(http);
        if (request == null || request.getEvents() == null || request.getEvents().isEmpty()) {
            return Result.OK("ok");
        }
        if (!verifySignature(http)) {
            log.warn("[analytics] 采集签名校验失败：ip={}", clientIp);
            return Result.error("signature invalid");
        }
        if (!allowTrack(clientIp)) {
            log.warn("[analytics] 采集触发限流：ip={}", clientIp);
            return Result.error("too many requests");
        }
        String storeCode = StringUtils.trimToNull(http.getHeader("X-Store-Code"));
        trackEventService.enqueue(request.getEvents(), storeCode, clientIp);
        return Result.OK("ok");
    }

    /** 简单签名：X-Track-Timestamp（epoch ms）+ X-Track-Sign = MD5(secret:timestamp)，5 分钟窗口 */
    private boolean verifySignature(HttpServletRequest request) {
        String secret = StringUtils.isNotBlank(sharedSignatureSecret)
                ? sharedSignatureSecret
                : properties.getSignatureSecret();
        if (StringUtils.isBlank(secret)) {
            return true;
        }
        String ts = request.getHeader("X-Track-Timestamp");
        String sign = request.getHeader("X-Track-Sign");
        if (StringUtils.isBlank(ts) || StringUtils.isBlank(sign)) {
            return false;
        }
        try {
            long timestamp = Long.parseLong(ts);
            if (Math.abs(System.currentTimeMillis() - timestamp) > SIGNATURE_WINDOW_MS) {
                return false;
            }
            String expect = DigestUtils.md5DigestAsHex((secret + ":" + ts).getBytes(StandardCharsets.UTF_8));
            return expect.equalsIgnoreCase(sign);
        } catch (Exception e) {
            return false;
        }
    }

    /** 单 IP 分钟级限流（进程内滑动窗口，粗粒度保护） */
    private boolean allowTrack(String clientIp) {
        int limit = properties.getTrackRateLimitPerMinute();
        if (limit <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        String key = StringUtils.defaultIfBlank(clientIp, "unknown");
        if (rateWindow.size() > 10_000) {
            rateWindow.clear();
        }
        Deque<Long> hits = rateWindow.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < windowStart) {
                hits.pollFirst();
            }
            if (hits.size() >= limit) {
                return false;
            }
            hits.addLast(now);
            return true;
        }
    }

    private String resolveIp(HttpServletRequest request) {
        try {
            return IpUtils.getIpAddr(request);
        } catch (Exception e) {
            return request == null ? null : request.getRemoteAddr();
        }
    }
}
