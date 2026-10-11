package org.jeecg.modules.shop.options.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.shop.options.entity.ShopOptions;
import org.jeecg.modules.shop.options.mapper.ShopOptionsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 规格预设字典：为商品编辑页提供"从预设库导入规格"数据源
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Tag(name = "规格预设字典")
@RestController
@RequestMapping("/shop/options")
@Slf4j
public class ShopOptionsController {

    @Autowired
    private ShopOptionsMapper shopOptionsMapper;

    /**
     * 全部启用的预设规格（不分页）
     */
    @Operation(summary = "规格预设-全部启用预设")
    @GetMapping(value = "/all")
    public Result<List<ShopOptions>> all() {
        QueryWrapper<ShopOptions> wrapper = new QueryWrapper<>();
        wrapper.eq("is_show", "Y").orderByAsc("sort_order");
        return Result.OK(shopOptionsMapper.selectList(wrapper));
    }
}
