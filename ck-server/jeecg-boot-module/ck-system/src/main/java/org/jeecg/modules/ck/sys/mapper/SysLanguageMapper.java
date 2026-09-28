package org.jeecg.modules.ck.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
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

@Mapper
public interface SysLanguageMapper extends BaseMapper<SysLanguage> {

    @Select("select * from ck_sys_language where enabled = 'Y' order by sort asc")
    List<SysLanguage> using();
}
