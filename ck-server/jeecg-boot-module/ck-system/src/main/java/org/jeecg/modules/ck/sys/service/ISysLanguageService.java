package org.jeecg.modules.ck.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.ck.sys.entity.SysLanguage;

import java.util.List;

/**
 * <p>
 * 系统语言支持
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-07-20
 */

public interface ISysLanguageService extends IService<SysLanguage> {
    List<SysLanguage> using();

}
