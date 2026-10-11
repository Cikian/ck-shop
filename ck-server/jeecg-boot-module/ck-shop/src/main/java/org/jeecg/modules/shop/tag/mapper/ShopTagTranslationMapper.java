package org.jeecg.modules.shop.tag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.tag.entity.ShopTagTranslation;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 标签多语言 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Mapper
public interface ShopTagTranslationMapper extends BaseMapper<ShopTagTranslation> {

    @Select("SELECT * FROM ck_shop_tag_translation WHERE tag_id = #{tagId}")
    List<ShopTagTranslation> selectByTagId(@Param("tagId") String tagId);

    /**
     * 按业务 id 批量查询。
     * 注意：不能用 BaseMapper.selectBatchIds —— 它按**翻译表自身主键 id** 查，
     * 而调用方传进来的是 tag_id，结果恒为空（本地化名称会静默回退成编码）。
     */
    @Select("<script>SELECT * FROM ck_shop_tag_translation WHERE tag_id IN "
            + "<foreach collection='tagIds' item='tid' open='(' separator=',' close=')'>#{tid}</foreach>"
            + "</script>")
    List<ShopTagTranslation> selectByTagIds(@Param("tagIds") Collection<String> tagIds);
}
