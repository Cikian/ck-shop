package org.jeecg.modules.shop.order.pay.mock;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * <p>
 * 模拟支付的唯一开关判定 + 启动自检。
 * </p>
 *
 * <h3>为什么还需要这一层</h3>
 * {@code MockPaymentGateway} 上的 {@code @Profile("dev")} 与
 * {@code @ConditionalOnProperty(matchIfMissing = false)} 已经决定了 Bean 是否存在，
 * 理论上"生产没有模拟支付"。但支付是资金链路，不能依赖"某个注解刚好没被改掉"：
 * <ul>
 *   <li><b>运行时</b>：{@link #isEnabled()} 同时要求"配置显式打开"和"当前是 dev profile"，
 *       任何一处不满足都按"没有模拟支付"处理，{@code prepay}/{@code mock-confirm} 一律拒绝；</li>
 *   <li><b>启动时</b>：{@link #run(ApplicationArguments)} 做两向自检——
 *       非 dev 环境检测到模拟网关 Bean，或配置说启用却没有网关 Bean，都直接让应用起不来。
 *       前者是"注解被人删了"，后者是"配置写了但没生效"，
 *       两种都不该带着"看起来能付款、其实付不了/能白拿"的状态上线。</li>
 * </ul>
 *
 * @author Cikian
 * @since 2026-10-22
 */
@Slf4j
@Component
public class MockPayGuard implements ApplicationRunner {

    /** 配置键：是否启用模拟支付。默认 false，只有 dev 的 application-dev.yml 显式打开 */
    public static final String ENABLED_KEY = "ck.shop.pay.mock.enabled";

    /** 唯一允许模拟支付存在的 profile */
    private static final String DEV_PROFILE = "dev";

    private final Environment environment;
    private final ObjectProvider<MockPaySupport> mockSupportProvider;

    public MockPayGuard(Environment environment, ObjectProvider<MockPaySupport> mockSupportProvider) {
        this.environment = environment;
        this.mockSupportProvider = mockSupportProvider;
    }

    /** 配置显式开启 **且** 当前是 dev profile，才算启用 */
    public boolean isEnabled() {
        boolean configured = Boolean.parseBoolean(
                environment.getProperty(ENABLED_KEY, "false").trim());
        return configured && devProfileActive();
    }

    /** 供业务流程做门禁：未启用时抛业务异常（而不是静默降级到其它渠道） */
    public void requireEnabled(String action) {
        if (!isEnabled()) {
            throw new JeecgBootException(action + "未启用：模拟支付只在 dev 环境可用，"
                    + "生产环境请使用真实支付渠道");
        }
    }

    /** 当前激活的 profile 里是否包含 dev */
    public boolean devProfileActive() {
        String[] profiles = environment.getActiveProfiles();
        return profiles != null && Arrays.asList(profiles).contains(DEV_PROFILE);
    }

    /**
     * 启动自检：任何"模拟支付可能被用到"的异常组合都直接让应用起不来。
     */
    @Override
    public void run(ApplicationArguments args) {
        boolean configured = Boolean.parseBoolean(
                environment.getProperty(ENABLED_KEY, "false").trim());
        boolean mockBeanPresent = mockSupportProvider.getIfAvailable() != null;
        boolean dev = devProfileActive();

        if (mockBeanPresent && !dev) {
            throw new IllegalStateException(
                    "检测到模拟支付网关在非 dev 环境注册（当前 profile=" + profiles()
                            + "）。模拟支付可让任何登录买家 0 元买下任意商品，"
                            + "生产/测试环境必须移除该 Bean（检查 @Profile/@ConditionalOnProperty 是否被改动）");
        }
        if (configured && !mockBeanPresent) {
            throw new IllegalStateException(
                    "配置 " + ENABLED_KEY + "=true，但模拟支付网关没有注册（当前 profile=" + profiles()
                            + "）。模拟支付只在 dev profile 生效；请确认启动 profile，"
                            + "或把该开关改为 false");
        }
        log.info("[模拟支付] 当前状态：{}（profile={}）", isEnabled() ? "已启用(仅dev)" : "已关闭", profiles());
    }

    private String profiles() {
        String[] profiles = environment.getActiveProfiles();
        return profiles == null || profiles.length == 0 ? "<default>" : String.join(",", profiles);
    }
}
