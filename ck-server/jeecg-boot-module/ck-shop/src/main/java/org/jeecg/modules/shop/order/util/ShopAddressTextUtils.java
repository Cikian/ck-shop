package org.jeecg.modules.shop.order.util;

import org.apache.commons.lang3.StringUtils;

/**
 * <p>
 * 收货地址整串文案的唯一实现（地址簿与订单共用）。
 * </p>
 *
 * <h3>为什么必须收敛成一个工具类</h3>
 * 同一份地址有两个使用方：地址簿（{@code AddressVO.fullAddress}）与订单详情
 * （{@code StoreOrderDetailVO.receiverFullAddress}）。这两处只要各写一遍拼接，
 * 就一定会漂移——A 处加了"相邻同名去重"、B 处忘了，用户就会看到
 * "曼谷, 曼谷"这种只有一边有多余字的整串，而没人知道哪个才是"对的面单"。
 * 所以规则只写在这里，两个调用方都调它。
 *
 * <h3>两套格式，按"有没有国家信息"分叉</h3>
 * <ul>
 *   <li><b>老式连写</b>（{@link #joinLegacy}）：省市区详址直接相接、无分隔符。
 *       这是改造前的中文地址格式，<b>一个字都不能改</b>——存量数据靠它保证展示不变；</li>
 *   <li><b>国际面单</b>（{@link #joinInternational}）：逗号 + 空格分隔，
 *       顺序 {@code 详址, 区, 市, 省, 邮编, 国家}，相邻同名去重。
 *       泰文/越南文/英文连着写，快递员无法断词；跨境面单缺国家等于地址不完整。</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-28
 */
public final class ShopAddressTextUtils {

    private ShopAddressTextUtils() {
    }

    /**
     * 有没有国家信息（ISO 码或国名任一有值）—— 决定走哪套拼接。
     *
     * <h3>为什么手填国名也算"有值"</h3>
     * 分支的意图是区分"跨境/非中文地址"与"改造前的老中文地址"。用户既然填了国名，
     * 这条地址就是跨境的，恰恰最需要分隔符和国家段；若只按国家码判断，
     * 手填国名的地址会掉回中文连写分支——既没有分隔符，又把已经存下来的国名丢掉。
     *
     * <p>反过来，码与名都为 null 才认作老数据：两列是同一条 DDL 一起加的，
     * 历史行必然都为空，所以这条判定与"country_code IS NULL"在存量数据上完全等价。
     */
    public static boolean hasCountry(String countryCode, String countryName) {
        return StringUtils.isNotBlank(countryCode) || StringUtils.isNotBlank(countryName);
    }

    /**
     * 老式连写（历史格式）：省 + 市 + 区 + 详址，直接相接，缺失的段跳过。
     *
     * <p><b>输出是历史约定，不许改</b>：老数据在地址列表/订单详情里显示的就是这一串，
     * 改动一个字符都算回归。
     */
    public static String joinLegacy(String province, String city, String district, String detailAddress) {
        StringBuilder sb = new StringBuilder();
        appendPlain(sb, province);
        appendPlain(sb, city);
        appendPlain(sb, district);
        appendPlain(sb, detailAddress);
        return sb.toString();
    }

    /**
     * 国际面单格式：{@code 详址, 区, 市, 省, 邮编, 国家}。
     *
     * <h3>三条规则</h3>
     * <ol>
     *   <li><b>逗号 + 空格分隔</b>：中文词间不需要分隔符，泰文/英文需要；</li>
     *   <li><b>相邻同值去重</b>：曼谷既是府又是市，不去重会拼出两个 {@code กรุงเทพฯ}。
     *       只去<b>相邻</b>重复，刻意不做全局去重——全局去重会把"同名区县"误删
     *       （区与市同名但中间隔着别的段），那是真的丢信息；</li>
     *   <li><b>空段跳过</b>：不留 {@code ", ,"} 这种多余分隔符。</li>
     * </ol>
     */
    public static String joinInternational(String... parts) {
        if (parts == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String previous = null;
        for (String part : parts) {
            String value = StringUtils.trimToNull(part);
            if (value == null || value.equals(previous)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(value);
            previous = value;
        }
        return sb.toString();
    }

    private static void appendPlain(StringBuilder sb, String part) {
        if (StringUtils.isNotBlank(part)) {
            sb.append(part.trim());
        }
    }
}
