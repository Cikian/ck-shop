package org.jeecg.modules.shop.store;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.jeecg.modules.shop.store.mapper.ShopStoreMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 店铺解析器：把一次请求归属到某个店铺。
 * </p>
 *
 * 解析顺序（刻意如此）：
 * <ol>
 *   <li>请求头 {@code X-Store-Code} —— 由前端站点的**服务端**（BFF）按自身配置注入，
 *       不出现在任何用户可见的 URL 里（商品链接要分享到社交平台，必须短）。</li>
 *   <li>Host 兜底 —— 每个店铺一个域名时，从 {@code ck_shop_store.domain} 反查。</li>
 *   <li>默认店铺 —— 本地开发/直连调试用，会打 warn 日志。</li>
 * </ol>
 *
 * <b>解析失败时绝不"返回全量"</b>：这是多店铺最危险的失误——
 * 漏了店铺条件就等于把 A 店的商品展示到 B 店。宁可报错。
 */
@Slf4j
@Component
public class StoreResolver {

    /**
     * 店铺编码请求头。前端站点服务端注入，前端浏览器不感知。
     */
    public static final String STORE_HEADER = "X-Store-Code";

    private final ShopStoreMapper shopStoreMapper;

    /**
     * 无法解析时的兜底店铺编码（本地开发用）。生产环境应保证 header 或域名一定能解析到。
     */
    @Value("${ck.shop.default-store-code:default}")
    private String defaultStoreCode;

    public StoreResolver(ShopStoreMapper shopStoreMapper) {
        this.shopStoreMapper = shopStoreMapper;
    }

    /**
     * 解析当前请求所属店铺。
     *
     * @throws JeecgBootException 编码/域名无效，或系统里一个可用店铺都没有
     */
    public ShopStore resolve(HttpServletRequest request) {
        // 1) 显式编码（前端站点服务端注入）
        String code = request == null ? null : request.getHeader(STORE_HEADER);
        if (StringUtils.isNotBlank(code)) {
            ShopStore store = shopStoreMapper.selectEnabledByCode(code.trim());
            if (store == null) {
                // 编码写了但查不到：说明配置错了或店铺已停用，必须显式失败
                throw new JeecgBootException("未识别的店铺编码：" + code + "（店铺不存在、已停用或已删除）");
            }
            return store;
        }

        // 2) 域名兜底（一店一域名时最省事）
        String host = request == null ? null : request.getServerName();
        if (StringUtils.isNotBlank(host)) {
            ShopStore store = shopStoreMapper.selectEnabledByDomain(host);
            if (store != null) {
                return store;
            }
        }

        // 3) 默认店铺：仅用于本地开发/直连调试
        ShopStore fallback = shopStoreMapper.selectEnabledByCode(defaultStoreCode);
        if (fallback == null) {
            throw new JeecgBootException("无法确定店铺：请先在【店铺管理】中创建店铺，"
                    + "或由前端站点通过 " + STORE_HEADER + " 请求头指定店铺编码");
        }
        // 用 String.format 拼好再打日志：避免 SLF4J 重载在 3 个占位参数下产生歧义
        log.warn(String.format(
                "请求未携带 %s 且 Host(%s) 未匹配任何店铺域名，已回退到默认店铺[%s]。"
                        + "生产环境请确保每个前台站点都配置了店铺编码。",
                STORE_HEADER, host, defaultStoreCode));
        return fallback;
    }

    /**
     * 仅取店铺ID，便于业务层直接使用。
     */
    public String resolveStoreId(HttpServletRequest request) {
        return resolve(request).getId();
    }
}
