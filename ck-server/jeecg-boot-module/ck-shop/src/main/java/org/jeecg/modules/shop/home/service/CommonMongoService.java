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

    List<Map<String, Object>> listSlideshow(String language);

    JSONArray getSlideshowGroupByLang();
}
