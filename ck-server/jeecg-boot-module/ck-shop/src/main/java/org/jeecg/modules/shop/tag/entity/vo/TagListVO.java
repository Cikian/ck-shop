package org.jeecg.modules.shop.tag.entity.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.tag.entity.ShopTag;
import org.jeecg.modules.shop.tag.entity.ShopTagTranslation;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 标签列表 VO：在实体基础上带出多语言名称
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class TagListVO extends ShopTag {

    /**
     * 多语言文案
     */
    private List<ShopTagTranslation> translations = new ArrayList<>();
}
