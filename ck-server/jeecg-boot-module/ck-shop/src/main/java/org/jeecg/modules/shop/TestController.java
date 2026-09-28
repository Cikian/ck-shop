package org.jeecg.modules.shop;


import com.mongodb.BasicDBObject;
import com.mongoplus.conditions.query.LambdaQueryChainWrapper;
import com.mongoplus.conditions.query.QueryWrapper;
import com.mongoplus.conditions.update.UpdateChainWrapper;
import com.mongoplus.conditions.update.UpdateWrapper;
import com.mongoplus.manager.MongoPlusClient;
import com.mongoplus.mapper.BaseMapper;
import com.mongoplus.mapping.TypeReference;
import org.jeecg.common.api.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 测试
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026/7/20
 */

@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private BaseMapper mongoMapper;
    @Autowired
    private MongoPlusClient mongoClient;

    @GetMapping("/set")
    public Result<?> set() {
        QueryWrapper<Object> qw = new QueryWrapper<>()
                .eq("username", "zhangsan");

        List<Map<String, Object>> list = mongoMapper.list("users", qw, new TypeReference<>() {});

        Map<String, Object> map = new HashMap<>();
        map.put("username", "Cikian");
        map.put("age", 25);

        mongoMapper.save("slideshow", map);

        mongoMapper.update("users", new UpdateWrapper<>().eq("username", "Cikian").set("age", 26).unset("ade"));

        return Result.OK(list);
    }
}
