package org.jeecg.modules.shop.tag.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.common.util.ShopAuditUtils;
import org.jeecg.modules.shop.tag.entity.ShopTag;
import org.jeecg.modules.shop.tag.entity.ShopTagTranslation;
import org.jeecg.modules.shop.tag.entity.dto.TagDTO;
import org.jeecg.modules.shop.tag.mapper.ShopTagMapper;
import org.jeecg.modules.shop.tag.mapper.ShopTagTranslationMapper;
import org.jeecg.modules.shop.tag.service.IShopTagService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 标签 Service 实现
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Service
public class ShopTagServiceImpl extends ServiceImpl<ShopTagMapper, ShopTag> implements IShopTagService {

    @Autowired
    private ShopTagTranslationMapper translationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveTag(TagDTO dto) {
        checkCodeUnique(dto.getCode(), null);

        ShopTag tag = new ShopTag();
        BeanUtils.copyProperties(dto, tag);
        tag.setId(IdWorker.getIdStr());
        // 删除标记只由 removeTag 维护：防止 client 传 delFlag=1 绕过 shop-tag:delete
        tag.setDelFlag(null);
        String username = ShopAuditUtils.currentUsername();
        Date now = new Date();
        tag.setCreateBy(username).setCreateTime(now).setUpdateBy(username).setUpdateTime(now);
        if (StringUtils.isBlank(tag.getIsActive())) {
            tag.setIsActive("Y");
        }

        super.save(tag);
        saveTranslations(tag.getId(), dto.getTranslations(), username, now);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTag(TagDTO dto) {
        if (StringUtils.isBlank(dto.getId())) {
            throw new JeecgBootException("未找到标签主键!");
        }
        checkCodeUnique(dto.getCode(), dto.getId());

        ShopTag tag = new ShopTag();
        BeanUtils.copyProperties(dto, tag);
        tag.setCreateBy(null).setCreateTime(null);
        // 删除标记只由 removeTag 维护：防止 client 传 delFlag=1 绕过 shop-tag:delete
        tag.setDelFlag(null);
        tag.setUpdateBy(ShopAuditUtils.currentUsername()).setUpdateTime(new Date());
        super.updateById(tag);

        String tagId = dto.getId();
        baseMapper.deleteTranslationsByTagId(tagId);
        String username = ShopAuditUtils.currentUsername();
        saveTranslations(tagId, dto.getTranslations(), username, new Date());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeTag(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("请选择要删除的标签!");
        }
        super.removeById(id);
        baseMapper.deleteTranslationsByTagId(id);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeTags(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new JeecgBootException("请至少选择一条数据!");
        }
        super.removeByIds(ids);
        baseMapper.deleteTranslationsByTagIds(ids);
        return true;
    }

    @Override
    public TagDTO getDtoById(String id) {
        ShopTag tag = super.getById(id);
        if (Objects.isNull(tag)) {
            throw new JeecgBootException("未找到数据!");
        }
        TagDTO dto = new TagDTO();
        BeanUtils.copyProperties(tag, dto);
        dto.setTranslations(translationMapper.selectByTagId(id));
        return dto;
    }

    private void saveTranslations(String tagId, List<ShopTagTranslation> translations, String username, Date now) {
        if (CollectionUtils.isEmpty(translations)) {
            return;
        }
        for (ShopTagTranslation translation : translations) {
            translation.setId(null);
            translation.setTagId(tagId);
            translation.setCreateBy(username);
            translation.setCreateTime(now);
            translation.setUpdateBy(username);
            translation.setUpdateTime(now);
            translationMapper.insert(translation);
        }
    }

    private void checkCodeUnique(String code, String excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        ShopTag exist = lambdaQuery().eq(ShopTag::getCode, code).one();
        if (exist != null && !exist.getId().equals(excludeId)) {
            throw new JeecgBootException("标签编码已存在：" + code);
        }
    }
}
