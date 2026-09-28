package org.jeecg.modules.ck.sys.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.ck.sys.entity.SysLanguage;
import org.jeecg.modules.ck.sys.mapper.SysLanguageMapper;
import org.jeecg.modules.ck.sys.service.ISysLanguageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

@Service
public class SysLanguageServiceImpl extends ServiceImpl<SysLanguageMapper, SysLanguage> implements ISysLanguageService {
    @Autowired
    private SysLanguageMapper langMapper;

    @Override
    public List<SysLanguage> using() {
        List<SysLanguage> using = langMapper.using();
        return using;
    }
}
