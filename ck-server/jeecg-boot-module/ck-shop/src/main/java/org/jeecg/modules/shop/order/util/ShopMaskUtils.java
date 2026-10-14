package org.jeecg.modules.shop.order.util;

import org.apache.commons.lang3.StringUtils;

/**
 * <p>
 * 敏感信息脱敏工具。
 * </p>
 *
 * 对应文档 §5「敏感信息泄露：卡号、身份证、手机号未脱敏」。
 * 原则：
 * <ul>
 *   <li><b>入库不脱敏</b>——收货手机号必须完整才能发货，脱敏后保存等于把数据弄丢了；</li>
 *   <li><b>出参按场景脱敏</b>——后台列表、日志、操作流水里一律脱敏；
 *       只有"买家查看自己的订单"和"发货所需的详情接口"返回完整号码。</li>
 * </ul>
 * 把脱敏做成工具函数而不是散落的 substring，是为了保证**所有出口用同一套规则**，
 * 不会出现"某个接口忘了脱敏"这种最容易漏的问题。
 *
 * @author Cikian
 * @since 2026-10-14
 */
public final class ShopMaskUtils {

    private ShopMaskUtils() {
    }

    /** 手机号脱敏：138****8000（保留前 3 后 4） */
    public static String maskPhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            return phone;
        }
        String value = phone.trim();
        if (value.length() < 7) {
            // 太短的数字全部打码，避免"1**"这种反而暴露了长度的写法看起来像没脱敏
            return repeat('*', value.length());
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    /** 姓名脱敏：张三 → 张*；欧阳修 → 欧**（保留姓，其余打码） */
    public static String maskName(String name) {
        if (StringUtils.isBlank(name)) {
            return name;
        }
        String value = name.trim();
        if (value.length() == 1) {
            return value;
        }
        return value.charAt(0) + repeat('*', value.length() - 1);
    }

    /**
     * 地址脱敏：保留省市，详细地址只留前 3 个字。
     * 用于后台列表与日志——两者都不需要完整门牌号。
     */
    public static String maskAddress(String detailAddress) {
        if (StringUtils.isBlank(detailAddress)) {
            return detailAddress;
        }
        String value = detailAddress.trim();
        if (value.length() <= 3) {
            return repeat('*', value.length());
        }
        return value.substring(0, 3) + repeat('*', Math.min(6, value.length() - 3));
    }

    /**
     * 支付报文脱敏：把回调原始 JSON 里可能出现的密钥/证书/身份证等字段打掉。
     *
     * <p>回调报文要留存用于对账排查，但**不能原样入库**：
     * 渠道回调里经常带 token、签名原文、买家账号。
     * 这里用"字段名匹配 + 值替换"的粗暴但确定的方式，宁可多打码也不要漏。
     */
    public static String maskNotifyBody(String body) {
        if (StringUtils.isBlank(body)) {
            return body;
        }
        String masked = body;
        String[] sensitiveKeys = {
                "token", "access_token", "secret", "api_key", "apikey", "appkey", "app_secret",
                "private_key", "privatekey", "password", "passwd", "id_card", "idcard",
                "cert", "certificate", "buyer_id", "openid", "sub_openid"
        };
        for (String key : sensitiveKeys) {
            masked = masked.replaceAll(
                    "(?i)(\"" + key + "\"\\s*:\\s*)\"[^\"]*\"",
                    "$1\"***\"");
        }
        return masked;
    }

    private static String repeat(char ch, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }
}
