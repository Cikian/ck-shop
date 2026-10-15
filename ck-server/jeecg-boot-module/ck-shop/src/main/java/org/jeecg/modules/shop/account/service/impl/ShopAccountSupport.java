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
     * 取客户端 IP：优先代理头（X-Forwarded-For 取**最后一段**，即最靠近本机的那一跳），
     * 都没有则用 remoteAddr；统一截断到 64 字符（落库字段长度限制）。
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
