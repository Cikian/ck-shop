package org.jeecg.modules.shop.home.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 通用无实体类service
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026/7/20
 */
public interface CommonMongoService {
    void saveSlideshow(JSONObject params);

    void editSlideshow(JSONObject params);

    void replaceAll(List<Map<String, Object>> params);

    /**
     * 根据 _id 删除轮播图
     *
     * @param ids 轮播图主键集合（可为单个）
     */
    void removeSlideshow(List<String> ids);

    /**
     * 批量更新轮播图排序值（只改动 sort 字段，不影响图片、标题等其他数据）
     *
     * @param sortList [{id: 轮播图主键, sort: 排序值}]
     */
    void updateSlideshowSort(List<Map<String, Object>> sortList);

    List<Map<String, Object>> listSlideshow(String language);

    JSONArray getSlideshowGroupByLang();
}
