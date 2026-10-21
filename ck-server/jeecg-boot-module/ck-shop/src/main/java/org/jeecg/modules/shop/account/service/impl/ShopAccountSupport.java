package org.jeecg.modules.shop.account.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 账号服务的**纯函数小工具**支撑组件（无状态、无数据库依赖）。
 *
 * <p>为什么单独拆出来：{@code ShopAccountServiceImpl} 原本 1000+ 行，登录/注册/改密等流程里
 * 夹着一堆"生成随机串 / 校验邮箱格式 / 取客户端 IP / 取 UA / 默认昵称"这类与业务流程无关的小函数。
 * 这里只搬这类**纯函数** —— 无副作用、可复用、可在别处直接单测。
 *
 * <p>注意：{@code formatDate} 仍留在原类（它依赖原类的私有日期格式常量，
 * 搬出来会造成两处定义，反而容易漂移）。
 */
@Component
public class ShopAccountSupport {

    /** 用户名后缀用的随机小写字母数字串（用户名规则：u_ + 8 位） */
    public String randomLowerAlnum(int length) {
        final String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** 邮箱格式（宽松校验：只要求 x@y.z，真正的唯一性由数据库与后端校验保证） */
    public boolean isValidEmail(String email) {
        return email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    /**
     * 取客户端 IP。
     *
     * <p><b>安全前提：代理头只允许在"请求来自可信代理"时采信。</b>
     * 旧实现无条件优先 {@code X-Forwarded-For}，攻击者只要直连后端端口、随手伪造
     * 这个头，就能让所有按 IP 的限流失效（每次换一个 IP 即可）。</p>
     *
     * <p>当前信任链：浏览器 → Nuxt BFF → Java 后端。BFF 会把自己算出的真实客户端 IP
     * 写进 {@code X-Client-IP}，后端只有在 {@code remoteAddr} 属于本机/内网
     * （即请求确实来自 BFF/反向代理）时才读它；公网直连的请求一律只认 remoteAddr。
     * 这样即使 9951 端口被暴露，伪造 {@code X-Client-IP} 也没有意义。</p>
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String remoteAddr = normalizeIp(request.getRemoteAddr());
        if (remoteAddr == null) {
            return null;
        }

        // 不是可信代理发来的请求：代理头全部不可信，只认 TCP 对端地址
        if (!isTrustedProxy(remoteAddr)) {
            return StringUtils.left(remoteAddr, 64);
        }

        // 1) BFF 显式注入的 X-Client-IP（优先）
        String injected = normalizeIp(request.getHeader("X-Client-IP"));
        if (injected != null) {
            return StringUtils.left(injected, 64);
        }

        // 2) 兼容其它反代：X-Forwarded-For 从左到右是 client, proxy1, proxy2...
        //    从右往左跳过可信代理，第一个不可信的地址就是真实客户端。
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(xff)) {
            String[] parts = xff.split(",");
            String fallback = null;
            for (int i = parts.length - 1; i >= 0; i--) {
                String ip = normalizeIp(parts[i]);
                if (ip == null) {
                    continue;
                }
                if (fallback == null) {
                    fallback = ip;
                }
                if (!isTrustedProxy(ip)) {
                    return StringUtils.left(ip, 64);
                }
            }
            if (fallback != null) {
                return StringUtils.left(fallback, 64);
            }
        }

        // 3) Nginx 常见写法：X-Real-IP
        String realIp = normalizeIp(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return StringUtils.left(realIp, 64);
        }

        // 4) 兜底：TCP 对端（此时它就是 BFF/代理本机地址）
        return StringUtils.left(remoteAddr, 64);
    }

    /** 去空白、去引号、过滤 "unknown"；明显不是 IP 的返回 null */
    private String normalizeIp(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String ip = raw.trim();
        if (ip.startsWith("\"") && ip.endsWith("\"") && ip.length() > 2) {
            ip = ip.substring(1, ip.length() - 1).trim();
        }
        if ("unknown".equalsIgnoreCase(ip) || StringUtils.isBlank(ip)) {
            return null;
        }
        if (ip.contains(":")) {
            // IPv6（可能是 [::1] 写法）
            if (ip.startsWith("[") && ip.endsWith("]")) {
                ip = ip.substring(1, ip.length() - 1);
            }
            return ip.length() <= 64 ? ip : null;
        }
        // IPv4 简单合法性校验：四段数字
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        for (String part : parts) {
            if (!part.matches("\\d{1,3}")) {
                return null;
            }
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return null;
            }
            if (value < 0 || value > 255) {
                return null;
            }
        }
        return ip;
    }

    /**
     * 是否为可信代理地址：本机回环 + 内网网段。
     * 这些地址只可能由本机/内网进程使用，公网攻击者无法把 TCP 对端伪装成它们。
     */
    private boolean isTrustedProxy(String ip) {
        if (ip == null) {
            return false;
        }
        if ("::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return true;
        }
        if (ip.contains(":")) {
            String lower = ip.toLowerCase();
            return lower.startsWith("fc") || lower.startsWith("fd")
                    || lower.startsWith("fe8") || lower.startsWith("fe9")
                    || lower.startsWith("fea") || lower.startsWith("feb");
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int a;
        int b;
        try {
            a = Integer.parseInt(parts[0]);
            b = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (a == 127) {
            return true;
        }
        if (a == 10) {
            return true;
        }
        if (a == 172 && b >= 16 && b <= 31) {
            return true;
        }
        if (a == 192 && b == 168) {
            return true;
        }
        // link-local（云主机元数据/内网探针常见）
        return a == 169 && b == 254;
    }

    /** 取 User-Agent（截断 500 字符） */
    public String resolveUserAgent(HttpServletRequest request) {
        return request == null ? null : StringUtils.left(request.getHeader("User-Agent"), 500);
    }

    /** 邮箱注册没填昵称时的默认昵称：取 @ 前缀，截断 100 字符 */
    public String defaultRealname(String email) {
        if (StringUtils.isBlank(email)) {
            return "用户";
        }
        int at = email.indexOf('@');
        String prefix = at > 0 ? email.substring(0, at) : email;
        return StringUtils.left(prefix, 100);
    }
}
