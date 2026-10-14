package org.jeecg.modules.shop.order.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.shop.order.service.OrderContext;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.jeecg.modules.shop.store.StoreResolver;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 订单请求上下文解析器：把一次 HTTP 请求翻译成 Service 需要的 {@code OrderContext}。
 * </p>
 *
 * <h3>为什么要收口成一个类</h3>
 * 订单模块有 7 个前台 Controller、十几个接口，每个都要做同样的四件事：
 * 取登录人、解析店铺、解析店铺部门编码、提取 IP/UA/来源。
 * 分散在每个 Controller 里手写，迟早会出现"某个接口忘了取店铺"——
 * 那就是 A 店的请求落到 B 店的订单上。收口后只有一处实现。
 *
 * <h3>fail-closed 原则</h3>
 * 登录人拿不到、店铺解析不到、店铺未绑部门，一律**报错**，
 * 绝不"降级成不过滤"或"写入 null 归属"。后者会制造一张
 * "在后台任何店铺筛选下都查不到的订单"。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Slf4j
@Component
public class OrderRequestContextResolver {

    @Autowired
    private StoreResolver storeResolver;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    /**
     * 构建上下文。
     *
     * @param requireStoreOrgCode 是否强制要求店铺已绑定部门。
     *                            结算/下单必须为 true（订单要落归属）；
     *                            纯读接口（如订单列表）可以为 false，
     *                            但仍然会带上解析结果，由业务自行降级。
     */
    public OrderContext build(HttpServletRequest request, boolean requireStoreOrgCode) {
        LoginUser user = orgScopeResolver.currentLoginUser();
        if (user == null || StringUtils.isBlank(user.getId())) {
            throw new JeecgBootException("请先登录后再操作订单");
        }
        ShopStore store = storeResolver.resolve(request);
        String storeOrgCode = orgScopeResolver.resolveStoreOrgCodeOrNull(store);
        if (requireStoreOrgCode && StringUtils.isBlank(storeOrgCode)) {
            throw new JeecgBootException("店铺[" + store.getName() + "]尚未绑定部门，暂时无法下单。"
                    + "请先到【店铺管理】中为它绑定部门");
        }

        return new OrderContext()
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setRealName(user.getRealname())
                .setStoreId(store.getId())
                .setStoreName(store.getName())
                .setStoreOrgCode(storeOrgCode)
                .setClientIp(resolveClientIp(request))
                .setUserAgent(resolveUserAgent(request))
                .setOrderSource(resolveSource(request))
                .setChannelCode(trimToNull(request.getParameter("channelCode")))
                .setUtmSource(trimToNull(request.getParameter("utmSource")))
                .setReferrer(trimToNull(request.getHeader("Referer")));
    }

    /** 当前登录人（后台接口用） */
    public LoginUser currentUser() {
        LoginUser user = orgScopeResolver.currentLoginUser();
        if (user == null || StringUtils.isBlank(user.getId())) {
            throw new JeecgBootException("请先登录");
        }
        return user;
    }

    public String currentUsername() {
        LoginUser user = orgScopeResolver.currentLoginUser();
        return user == null ? "SYSTEM" : user.getUsername();
    }

    /**
     * 取真实客户端 IP。
     *
     * <p>按代理链从后往前找**第一个非内网地址**，而不是直接取
     * {@code X-Forwarded-For} 的第一段：第一段是客户端可以随便伪造的，
     * 用它做风控等于把风控交给了攻击者。这个 IP 只用于风控与排查，
     * 不作为任何安全判断的唯一依据。
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String[] candidates = {
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getHeader("Proxy-Client-IP"),
                request.getHeader("WL-Proxy-Client-IP"),
                request.getRemoteAddr()
        };
        for (String candidate : candidates) {
            if (StringUtils.isBlank(candidate) || "unknown".equalsIgnoreCase(candidate)) {
                continue;
            }
            // X-Forwarded-For 可能是 "client, proxy1, proxy2"
            String[] parts = candidate.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String ip = parts[i].trim();
                if (StringUtils.isNotBlank(ip)) {
                    return StringUtils.left(ip, 64);
                }
            }
        }
        return null;
    }

    public String resolveUserAgent(HttpServletRequest request) {
        return request == null ? null : StringUtils.left(request.getHeader("User-Agent"), 500);
    }

    /** 下单来源：优先取显式参数，其次按 UA 猜（H5/App），最后兜底 web */
    public String resolveSource(HttpServletRequest request) {
        if (request == null) {
            return "api";
        }
        String source = trimToNull(request.getParameter("orderSource"));
        if (source != null) {
            return source.toLowerCase();
        }
        String ua = StringUtils.defaultString(request.getHeader("User-Agent")).toLowerCase();
        if (ua.contains("micromessenger") || ua.contains("mobile") || ua.contains("android")
                || ua.contains("iphone")) {
            return "h5";
        }
        if (StringUtils.isBlank(ua)) {
            return "api";
        }
        return "web";
    }

    private String trimToNull(String value) {
        return StringUtils.trimToNull(value);
    }
}
