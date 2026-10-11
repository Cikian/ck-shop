package org.jeecg.modules.shop.tag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.tag.entity.ShopTag;
import org.jeecg.modules.shop.tag.entity.dto.TagDTO;

import java.util.List;

/**
 * <p>
 * 标签 Service
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */
public interface IShopTagService extends IService<ShopTag> {

    /**
     * 新增标签（含多语言）
     */
    boolean saveTag(TagDTO dto);

    /**
     * 编辑标签（含多语言）
     */
    boolean updateTag(TagDTO dto);

    /**
     * 删除标签（含多语言、商品关联、软删除）
     */
    boolean removeTag(String id);

    /**
     * 批量删除标签
     */
    boolean removeTags(List<String> ids);

    /**
     * 按ID查询标签（含多语言）
     */
    TagDTO getDtoById(String id);
}
