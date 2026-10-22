package org.jeecg.modules.shop.common;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

/**
 * <p>
 * 成本/毛利字段的字段级权限。
 * </p>
 *
 * <p>背景：成本价、毛利属于经营敏感信息，只有部分岗位（老板/财务）应该看到。
 * 以前它们只受页面级 {@code shop-order:detail} / {@code shop-order:stats} 控制 ——
 * 任何有订单详情权限的运营都能从接口里直接拿到成本与毛利（甚至可以绕过前端直接调接口）。
 *
 * <p>所以必须在**服务端**做字段级裁剪：没有 {@code shop-order:cost} 时，
 * 控制器把成本/毛利字段置空，前端拿不到真实数据；前端再按同一权限码隐藏卡片。
 *
 * <p>为什么不用 {@code @RequiresPermissions} 单独开接口：成本字段是订单详情/统计的一部分，
 * 拆接口会让调用方多一次请求，也容易出现"详情里有、列表里没有"的口径分裂。
 */
public final class ShopCostPermission {

    /** 成本/毛利查看权限（菜单权限表里独立配置） */
    public static final String PERM = "shop-order:cost";

    private ShopCostPermission() {
    }

    /**
     * 当前登录用户是否可以查看成本/毛利。
     *
     * <p>匿名/定时任务等没有 Subject 的场景一律返回 false（默认拒绝）。
     */
    public static boolean canViewCost() {
        try {
            Subject subject = SecurityUtils.getSubject();
            return subject != null && subject.isPermitted(PERM);
        } catch (Exception e) {
            // 非 Web 线程（定时任务）没有绑定 Subject，按无权限处理
            return false;
        }
    }
}
