package org.jeecg.modules.shop.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.shop.test.entity.JeecgDemo;
import org.jeecg.modules.shop.test.mapper.JeecgDemoMapper;
import org.jeecg.modules.shop.test.service.IJeecgDynamicDataService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Description: 动态数据源测试
 * @Author: zyf
 * @Date:2020-04-21
 */
@Service
public class JeecgDynamicDataServiceImpl extends ServiceImpl<JeecgDemoMapper, JeecgDemo> implements IJeecgDynamicDataService {

    @Override
    public List<JeecgDemo> selectSpelByHeader() {
        return list();
    }

    @Override
    public List<JeecgDemo> selectSpelByKey(String dsName) {
        return list();
    }
}
