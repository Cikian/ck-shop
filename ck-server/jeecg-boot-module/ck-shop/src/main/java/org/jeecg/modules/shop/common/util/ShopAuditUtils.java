package org.jeecg.modules.shop.common.util;

import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.vo.LoginUser;

/**
 * <p>
 * 店铺模块通用工具
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */
public final class ShopAuditUtils {

    private ShopAuditUtils() {
    }

    /**
     * 获取当前登录用户名，未登录（如公开接口）返回 "system"
     *
     * @return 用户名
     */
    public static String currentUsername() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            if (principal instanceof LoginUser) {
                String username = ((LoginUser) principal).getUsername();
                if (username != null && !username.isEmpty()) {
                    return username;
                }
            }
        } catch (Exception e) {
            // 公开接口或未登录场景，忽略
        }
        return "system";
    }
}
