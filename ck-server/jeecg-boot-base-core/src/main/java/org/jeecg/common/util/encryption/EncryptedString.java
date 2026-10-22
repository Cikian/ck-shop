package org.jeecg.common.util.encryption;


import lombok.Data;

/**
 * @Description: EncryptedString
 * @author: jeecg-boot
 */
@Data
public class  EncryptedString {

    /**
     * 长度为16个字符。
     *
     * 轮换（清单 B17）：优先读环境变量 JEECG_AES_KEY（见 ck-server/.env），
     * 未配置时才回退到 JeecgBoot 开源默认值。该密钥用于**传输中**的口令加密
     * （前端 AES 加密口令 → 后端解密），不涉及已落库数据，因此可以安全轮换，
     * 只要前后端同时改并重启。
     */
    public static String key = resolve("JEECG_AES_KEY", "1234567890adbcde");

    /**
     * 长度为16个字符，与 key 同一套轮换规则（JEECG_AES_IV）。
     */
    public static String iv = resolve("JEECG_AES_IV", "1234567890hjlkew");

    private static String resolve(String envName, String fallback) {
        String value = System.getenv(envName);
        if (value == null || value.trim().isEmpty()) {
            value = System.getProperty(envName);
        }
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }
}
