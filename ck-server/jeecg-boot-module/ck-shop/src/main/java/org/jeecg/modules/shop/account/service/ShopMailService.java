package org.jeecg.modules.shop.account.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.config.StaticConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 店铺端邮件发送
 * </p>
 *
 * <b>为什么自己写一个而不复用它：</b>
 * {@code EmailSendMsgHandle.sendMsg} 把发送丢进一个 1024 线程的
 * {@code ThreadPoolExecutor(0, 1024, ...)} 且**不捕获非 MessagingException 异常**，
 * 同时它绑定的是"系统消息"那套 DTO。店铺端只需要"发一封验证码邮件"，
 * 失败必须能让调用方知道（否则用户等 5 分钟等不到码，而我们日志里什么都没有）。
 *
 * <b>同步发送的取舍：</b>
 * {@code JavaMailSender.send} 是阻塞的，发验证码接口会一直占住请求线程，
 * 直到 SMTP 返回或某一阶段超时。各 profile 的连接/读/写超时已分别收紧为
 * 5/15/5 秒（均为单次 I/O 超时，不是整封邮件的全程硬上限）；店铺端 BFF
 * 对本接口最多等待 30 秒。这里接受同步阻塞的代价，因为：
 * <ul>
 *   <li>异步发送的话，"发送失败"用户在浏览器里永远看不到，只会反复点重发；</li>
 *   <li>接口有 60 秒冷却兜底，不可能被打成高频调用；</li>
 *   <li>各阶段均有超时，不会无限期挂住；调整它们时必须同步检查 BFF 的 30 秒上限。</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Slf4j
@Service
public class ShopMailService {

    /**
     * 用 ObjectProvider 而不是直接 @Autowired JavaMailSender：
     * 若某个部署环境没有引入 spring-boot-starter-mail 或未配置 spring.mail.host，
     * 直接注入会让**整个应用启动失败**。这里允许缺失，
     * 在真正发信时抛明确的业务异常。
     */
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private final StaticConfig staticConfig;

    /** 发件人兜底：spring.mail.username 为空时用它 */
    @Value("${ck.shop.mail.from:}")
    private String mailFromFallback;

    @Autowired
    public ShopMailService(ObjectProvider<JavaMailSender> mailSenderProvider, StaticConfig staticConfig) {
        this.mailSenderProvider = mailSenderProvider;
        this.staticConfig = staticConfig;
    }

    /**
     * 发送一封 HTML 邮件。
     *
     * @throws JeecgBootException 邮件服务未配置 / 发件人为空 / 发送失败
     */
    public void sendHtml(String to, String subject, String html) {
        if (StringUtils.isBlank(to)) {
            throw new JeecgBootException("收件邮箱为空，无法发送邮件");
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new JeecgBootException("邮件服务未配置（缺少 spring.mail 相关配置），无法发送验证码邮件，请联系管理员");
        }
        String from = resolveFrom();
        if (StringUtils.isBlank(from)) {
            throw new JeecgBootException("邮件发件人未配置（spring.mail.username 为空），无法发送验证码邮件，请联系管理员");
        }
        // 未配置 host 的 JavaMailSenderImpl 会在 send 时抛 MailSendException，
        // 提前判一次能给出更贴合"配置缺失"的文案
        if (mailSender instanceof JavaMailSenderImpl impl && StringUtils.isBlank(impl.getHost())) {
            throw new JeecgBootException("邮件服务未配置（spring.mail.host 为空），无法发送验证码邮件，请联系管理员");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("店铺端验证码邮件发送成功，收件人：{}", maskEmail(to));
        } catch (Exception e) {
            // 不把 SMTP 原始异常抛给前端：它可能含服务器地址、账号等基础设施信息
            log.error("店铺端验证码邮件发送失败，收件人：{}", maskEmail(to), e);
            throw new JeecgBootException("验证码邮件发送失败，请稍后重试或联系管理员");
        }
    }

    /**
     * 发件人优先级：{@code ck.shop.mail.from} → {@code spring.mail.username}。
     * 后者是 JeecgBoot 既有约定（{@code StaticConfig.emailFrom}），保持兼容。
     */
    private String resolveFrom() {
        if (StringUtils.isNotBlank(mailFromFallback)) {
            return mailFromFallback.trim();
        }
        return staticConfig == null ? null : StringUtils.trimToNull(staticConfig.getEmailFrom());
    }

    /**
     * 日志脱敏：`abcd@gmail.com` → `a***@gmail.com`。
     * 日志会被收集/转发，完整邮箱属于个人信息，能少落一份就少一份。
     */
    private String maskEmail(String email) {
        if (StringUtils.isBlank(email)) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at < 0 ? "" : email.substring(at));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
