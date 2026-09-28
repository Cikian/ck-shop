package org.jeecg.modules.shop.home.service.impl;


import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mongoplus.conditions.interfaces.Projection;
import com.mongoplus.conditions.query.QueryWrapper;
import com.mongoplus.conditions.update.UpdateWrapper;
import com.mongoplus.mapper.BaseMapper;
import com.mongoplus.mapper.MongoMapper;
import com.mongoplus.mapping.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.ck.sys.entity.SysLanguage;
import org.jeecg.modules.ck.sys.mapper.SysLanguageMapper;
import org.jeecg.modules.shop.home.service.CommonMongoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
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

@Service
public class CommonMongoServiceImpl implements CommonMongoService {
    @Autowired
    private BaseMapper mongoMapper;
    @Autowired
    private SysLanguageMapper lanMapper;


    @Override
    public void saveSlideshow(JSONObject params) {
        List<SysLanguage> usingLang = lanMapper.using();
        List<LangKeys> langKeysList = LangKeys.getUsingLangKeys(usingLang);

        Map<String, Object> obj = new HashMap<>();
        obj.put("sort", params.getInteger("sort"));
        obj.put("enabled", params.getString("enabled"));
        obj.put("desc", params.getString("desc"));
        obj.put("goods", params.getString("goods"));
        String targetUrl = params.getString("targetUrl");
        if (StringUtils.isNotEmpty(targetUrl)) {
            obj.put("targetUrl", (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) ? params.getString("targetUrl") : "http://" + params.getString("targetUrl"));
        }
        for (LangKeys langKeys : langKeysList) {
            obj.put(langKeys.picUrlKey, params.get(langKeys.langCode) != null ? params.getJSONObject(langKeys.langCode).getString(langKeys.picUrlKey) : null);
            obj.put(langKeys.titleKey, params.get(langKeys.titleKey) != null ? params.getJSONObject(langKeys.langCode).getString(langKeys.titleKey) : null);
        }

        mongoMapper.save("slideshow", obj);
    }

    @Override
    public void editSlideshow(JSONObject params) {
        List<SysLanguage> usingLang = lanMapper.using();
        List<LangKeys> langKeysList = LangKeys.getUsingLangKeys(usingLang);

        Map<String, Object> obj = new HashMap<>();
        obj.put("_id", params.getString("id"));
        obj.put("sort", params.getInteger("sort"));
        obj.put("enabled", params.getString("enabled"));
        obj.put("desc", params.getString("desc"));
        obj.put("goods", params.getString("goods"));
        String targetUrl = params.getString("targetUrl");
        if (StringUtils.isNotEmpty(targetUrl)) {
            obj.put("targetUrl", (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) ? params.getString("targetUrl") : "http://" + params.getString("targetUrl"));
        }
        for (LangKeys langKeys : langKeysList) {
            obj.put(langKeys.picUrlKey, params.get(langKeys.langCode) != null ? params.getJSONObject(langKeys.langCode).getString(langKeys.picUrlKey) : null);
            obj.put(langKeys.titleKey, params.get(langKeys.titleKey) != null ? params.getJSONObject(langKeys.langCode).getString(langKeys.titleKey) : null);
        }

        mongoMapper.update("slideshow", obj, new QueryWrapper<>().eq("_id", obj.get("_id")));
    }

    @Override
    public void replaceAll(List<Map<String, Object>> params) {
        mongoMapper.remove("slideshow", new UpdateWrapper<>().exists("_id", true));
        mongoMapper.saveBatch("slideshow", params);
    }

    @Override
    public void removeSlideshow(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 逐条按 _id 删除：mongoplus 会将字符串主键转换成 ObjectId 参与匹配
        for (String id : ids) {
            if (StringUtils.isBlank(id)) {
                continue;
            }
            mongoMapper.remove("slideshow", new UpdateWrapper<>().eq("_id", id));
        }
    }

    @Override
    public void updateSlideshowSort(List<Map<String, Object>> sortList) {
        if (sortList == null || sortList.isEmpty()) {
            return;
        }
        for (Map<String, Object> item : sortList) {
            if (item == null) {
                continue;
            }
            Object id = item.get("id");
            Object sort = item.get("sort");
            if (id == null || sort == null) {
                continue;
            }
            // 只更新 sort 字段，避免整体覆盖导致图片、标题丢失
            mongoMapper.update("slideshow", new UpdateWrapper<>().eq("_id", id.toString()).set("sort", sort));
        }
    }

    @Override
    public List<Map<String, Object>> listSlideshow(String language) {
        String titleKey = "title_" + language;
        String picUrlKey = "pic_url_" + language;

        QueryWrapper<Object> qw = new QueryWrapper<>().project(
                new Projection("_id", 1),
                new Projection("order", 1),
                new Projection("goods", 1),
                new Projection("title_en", 1),
                new Projection("pic_url_en", 1),
                new Projection("targetUrl", 1),
                new Projection(titleKey, 1),
                new Projection(picUrlKey, 1)
        ).eq("enabled", "Y");

        List<Map<String, Object>> list = mongoMapper.list("slideshow", qw, new TypeReference<>() {});

        // 在 Java 内存中做回退处理
        for (Map<String, Object> map : list) {
            // 1. 处理 title
            Object titleVal = map.remove(titleKey);
            Object titleEnVal = map.remove("title_en");
            map.put("title", (titleVal != null && !"".equals(titleVal)) ? titleVal : titleEnVal);

            // 2. 处理 pic_url
            Object picVal = map.remove(picUrlKey);
            Object picEnVal = map.remove("pic_url_en");
            map.put("pic_url", (picVal != null && !"".equals(picVal)) ? picVal : picEnVal);

            // 3. 处理 _id 格式（防止前端拿到复杂 ObjectId 对象）
            if (map.containsKey("_id") && map.get("_id") != null) {
                map.put("_id", map.get("_id").toString());
            }
        }

        return list;
    }

    /**
     * 获取所有轮播图（以language分组）
     * @return
     */
    @Override
    public JSONArray getSlideshowGroupByLang() {
        List<Map<String, Object>> all = mongoMapper.list("slideshow", new TypeReference<>() {});

        if (all == null || all.isEmpty()) {
            return new JSONArray();
        }

        // 1. 预先构建各语言对应的 key 配置，将字符串拼接移出内层循环
        List<SysLanguage> usingLang = lanMapper.using();
        List<LangKeys> langKeysList = LangKeys.getUsingLangKeys(usingLang);

        // 2. 初始化结果数组
        JSONArray resultArray = new JSONArray();

        // 3. 遍历数据组装 JSON
        for (Map<String, Object> item : all) {
            JSONObject slideObj = new JSONObject();

            // 填充公共属性
            slideObj.put("id", item.get("_id").toString());
            slideObj.put("desc", item.get("desc"));
            slideObj.put("sort", item.get("sort"));
            slideObj.put("goods", item.get("goods"));
            // targetUrl 可能为 null，此处回填空字符串，避免前端拿到 "null" 文本
            Object targetUrl = item.get("targetUrl");
            slideObj.put("targetUrl", targetUrl == null ? "" : targetUrl.toString().replace("http://", "").replace("https://", ""));
            slideObj.put("enabled", item.get("enabled"));

            // 填充各语言属性
            for (LangKeys langKeys : langKeysList) {
                JSONObject langObj = new JSONObject();

                // Fastjson2 在 put null 值时也会保留 key
                langObj.put(langKeys.picUrlKey, item.get(langKeys.picUrlKey));
                langObj.put(langKeys.titleKey, item.get(langKeys.titleKey));

                slideObj.put(langKeys.langCode, langObj);
            }

            resultArray.add(slideObj);
        }

        return resultArray;
    }

    /**
     * 多语言属性预提取配置类，避免循环内重复拼接字符串
     */
    private static class LangKeys {
        final String langCode;
        final String picUrlKey;
        final String titleKey;

        LangKeys(String langCode) {
            this.langCode = langCode;
            this.picUrlKey = "pic_url_" + langCode;
            this.titleKey = "title_" + langCode;
        }

        public static List<LangKeys> getUsingLangKeys(List<SysLanguage> usingLang) {
            List<LangKeys> langKeysList = new ArrayList<>(usingLang.size());
            for (SysLanguage sysLanguage : usingLang) {
                if (sysLanguage != null && sysLanguage.getCode() != null) {
                    langKeysList.add(new LangKeys(sysLanguage.getCode()));
                }
            }
            return langKeysList;
        }

    }
}
