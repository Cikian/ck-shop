package org.jeecg.modules.shop.account.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.shop.account.entity.ShopLoginLog;
import org.jeecg.modules.shop.account.mapper.ShopLoginLogMapper;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * <p>
 * 店铺端登录流水记录器
 * </p>
 *
 * <b>为什么写流水永远不能影响登录结果：</b>
 * 记录登录日志是**旁路**能力。它一旦抛异常，用户就会因为"日志表满了 / 磁盘满了"
 * 这类与登录毫无关系的原因登不进来 —— 那是把可观测性做成了可用性风险。
 * 因此这里统一 catch 所有异常并降级为 warn 日志。
 *
 * 与 {@code LoginController} 用 {@code baseCommonService.addLog} 写 sys_log 的分工：
 * 那个是管理端操作日志（谁在后台做了什么），这里是店铺端**登录来源**流水
 * （C 端用户从哪个店铺登的），两者的查询口径与保留策略都不同，不复用。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Slf4j
@Service
public class ShopLoginLogRecorder {

    private final ShopLoginLogMapper shopLoginLogMapper;

    public ShopLoginLogRecorder(ShopLoginLogMapper shopLoginLogMapper) {
        this.shopLoginLogMapper = shopLoginLogMapper;
    }

    /**
     * 记录一次登录尝试。
     *
     * @param userId      用户ID，可为 null（如"邮箱未注册"这类失败）
     * @param username    用户账号，可为 null
     * @param email       登录邮箱
     * @param loginType   PASSWORD / EMAIL_CODE / GOOGLE
     * @param store       当次请求解析到的店铺，可为 null（解析失败时）
     * @param clientIp    客户端 IP
     * @param userAgent   User-Agent
     * @param success     是否成功
     * @param failReason  失败原因，成功时传 null
     */
    public void record(String userId, String username, String email, String loginType,
                       ShopStore store, String clientIp, String userAgent,
                       boolean success, String failReason) {
        try {
            ShopLoginLog entity = new ShopLoginLog()
                    .setUserId(userId)
                    .setUsername(username)
                    .setEmail(email)
                    .setLoginType(loginType)
                    .setStoreCode(store == null ? null : store.getCode())
                    .setStoreId(store == null ? null : store.getId())
                    .setClientIp(clientIp)
                    .setUserAgent(userAgent)
                    .setLoginTime(new Date())
                    .setSuccess(success ? 1 : 0)
                    // 失败原因列是 varchar(200)，超长会被 MySQL 截断（严格模式下报错），
                    // 这里主动截断，保证"日志写不进去"永远不会发生
                    .setFailReason(StringUtils.left(failReason, 200));
            shopLoginLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("写入店铺端登录流水失败（不影响登录结果），loginType={}，email={}，原因={}",
                    loginType, email, e.getMessage());
        }
    }
}
