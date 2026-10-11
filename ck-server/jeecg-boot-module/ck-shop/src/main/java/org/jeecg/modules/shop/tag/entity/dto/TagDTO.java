package org.jeecg.modules.shop.tag.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.tag.entity.ShopTag;
import org.jeecg.modules.shop.tag.entity.ShopTagTranslation;

import java.util.List;

/**
 * <p>
 * 标签新增&修改 DTO
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class TagDTO extends ShopTag {
    /**
     * 多语言文案
     */
    private List<ShopTagTranslation> translations;
}
