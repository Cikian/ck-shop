package org.jeecg.common.util.encryption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 用 Spring 配置覆盖 AES 口令加密密钥（清单 B17）。
 *
 * <h3>为什么需要这个类</h3>
 * {@link EncryptedString} 是静态工具类，被 {@link AesEncryptUtil} 等静态方法直接引用。
 * 它的 key/iv 只能读 {@code System.getenv()} / {@code System.getProperty()}，
 * 而本项目的敏感配置放在 {@code ck-server/.env}，由 Spring 的
 * {@code spring.config.import} 加载进 **Spring Environment** ——
 * 进程环境变量里是**没有**这些值的。
 *
 * <p>如果只在 {@code System.getenv()} 里找，轮换后的值永远读不到，
 * 后端会静默回退到开源默认 key，而店铺端用的是新 key ——
 * 表现就是"登录时提示口令错误"，且日志里没有任何线索。
 *
 * <h3>做法</h3>
 * 启动时（任何请求之前）用 Environment 里的值覆盖静态字段；
 * {@link AesEncryptUtil} 已改成在调用点读取，不会在类初始化时提前捕获旧值。
 *
 * <p>配置项（application-dev.yml / .env）：
 * <ul>
 *   <li>{@code jeecg.encrypt.aes-key} 或环境变量 {@code JEECG_AES_KEY}</li>
 *   <li>{@code jeecg.encrypt.aes-iv} 或环境变量 {@code JEECG_AES_IV}</li>
 * </ul>
 */
@Slf4j
@Component
/*
 * @Lazy(false) 是必须的：dev 配置开了 spring.main.lazy-initialization=true，
 * 而本类没有被任何 bean 注入 —— 默认懒加载下它**永远不会被创建**，
 * 密钥也就永远不会被覆盖（实测：/sys/getEncryptedString 仍返回开源默认 key）。
 */
@Lazy(false)
public class EncryptedStringInitializer {

    private static final int AES_KEY_LENGTH = 16;

    public EncryptedStringInitializer(Environment environment) {
        applyKey(environment);
        applyIv(environment);
    }

    private void applyKey(Environment environment) {
        String value = firstNonBlank(
                environment.getProperty("jeecg.encrypt.aes-key"),
                environment.getProperty("JEECG_AES_KEY"));
        if (value == null) {
            log.warn("[EncryptedString] 未配置 jeecg.encrypt.aes-key / JEECG_AES_KEY，"
                    + "继续使用代码内默认密钥（仅限本地开发，生产必须配置，见清单 B17）");
            return;
        }
        if (value.length() != AES_KEY_LENGTH) {
            throw new IllegalStateException("AES key 必须是 " + AES_KEY_LENGTH + " 个字符，当前长度 " + value.length());
        }
        EncryptedString.key = value;
    }

    private void applyIv(Environment environment) {
        String value = firstNonBlank(
                environment.getProperty("jeecg.encrypt.aes-iv"),
                environment.getProperty("JEECG_AES_IV"));
        if (value == null) {
            log.warn("[EncryptedString] 未配置 jeecg.encrypt.aes-iv / JEECG_AES_IV，"
                    + "继续使用代码内默认 iv（仅限本地开发）");
            return;
        }
        if (value.length() != AES_KEY_LENGTH) {
            throw new IllegalStateException("AES iv 必须是 " + AES_KEY_LENGTH + " 个字符，当前长度 " + value.length());
        }
        EncryptedString.iv = value;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return null;
    }
}
