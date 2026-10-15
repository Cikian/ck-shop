package org.jeecg.modules.shop.common;

import org.apache.commons.lang3.StringUtils;

/**
 * 店铺（多租户）可见性判断的唯一实现。
 *
 * <p>为什么要有这个类：同一条规则"商品的 {@code sys_org_code} 必须落在当前店铺绑定部门的 org_code 前缀之下"
 * 原本在**多个服务里各写了一份**（商品、购物车…），这属于安全边界规则——
 * 一处漏判就等于某个店铺能看见/操作别的店铺的商品。收敛到一处后，规则只有一种写法。
 *
 * <p>⚠️ 与既有实现的差异（这是一次**收紧**，不是等价替换）：
 * 旧代码里出现过 {@code productOrgCode.startsWith(storeOrgCode)} 这种直接调用。
 * 当 {@code storeOrgCode} 为空串时，{@code startsWith("")} 恒为 true —— 等于店铺校验被绕过。
 * 本方法显式要求两侧都非空白，从根上消除这种情况。
 */
public final class ShopOrgUtils {

    private ShopOrgUtils() {
    }

    /**
     * 商品归属的组织码是否落在店铺组织码前缀之下。
     *
     * @param productOrgCode 商品 {@code sys_org_code}（可为 null/空白 → 判定为不可见）
     * @param storeOrgCode   当前店铺绑定部门的 org_code（可为 null/空白 → 判定为不可见，绝不放过）
     */
    public static boolean matchesOrg(String productOrgCode, String storeOrgCode) {
        return StringUtils.isNotBlank(productOrgCode)
                && StringUtils.isNotBlank(storeOrgCode)
                && productOrgCode.startsWith(storeOrgCode);
    }
}
