package org.jeecg.modules.shop.home.service.impl;


import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mongoplus.conditions.interfaces.Projection;
import com.mongoplus.conditions.query.QueryWrapper;
import com.mongoplus.conditions.update.UpdateWrapper;
import com.mongoplus.mapper.BaseMapper;
import com.mongoplus.mapping.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.jeecg.modules.ck.sys.entity.SysLanguage;
import org.jeecg.modules.ck.sys.mapper.SysLanguageMapper;
import org.jeecg.modules.shop.home.service.CommonMongoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 通用无实体类service（首页轮播图）
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

    /** 轮播图集合名 */
    private static final String COLLECTION = "slideshow";

    /**
     * 归属部门编码列（与商品/分类同名的隔离维度）。
     * 老文档没有这一列，读不到就等于"不属于任何店铺"——历史上是全局共享，
     * 现已通过数据迁移统一归入默认店铺部门（见 ck-server/db/ck-shop-slideshow-org-scope.js）。
     */
    private static final String SYS_ORG_CODE = "sys_org_code";

    /** 排序字段：**写入与读取都用它**（历史上店铺端投影的是不存在的 `order`，导致后台排序对前台无效） */
    private static final String SORT_FIELD = "sort";

    @Autowired
    private BaseMapper mongoMapper;
    @Autowired
    private SysLanguageMapper lanMapper;


    @Override
    public String saveSlideshow(JSONObject params, String orgCode) {
        Map<String, Object> obj = buildSlideBody(params);
        // 归属：由 controller 解析并校验后传入，绝不在这里兜底成 null
        obj.put(SYS_ORG_CODE, orgCode);
        // 主键自己在 Java 侧生成，才能把 id 返回给前端（否则新增后拿不到主键，
        // 前端的"自动保存"会把同一条草稿反复 POST 成多条）
        ObjectId id = new ObjectId();
        obj.put("_id", id);
        mongoMapper.save(COLLECTION, obj);
        return id.toHexString();
    }

    @Override
    public void editSlideshow(JSONObject params, String whereOrgCode, String setOrgCode) {
        Map<String, Object> obj = buildSlideBody(params);
        obj.put("_id", params.getString("id"));
        obj.put(SYS_ORG_CODE, setOrgCode);

        /*
         * 条件用**库里现值**（whereOrgCode），写入用目标值（setOrgCode）：
         * 之前两边都用目标值，管理员把 A 店轮播图改到 B 店时条件带的是 B、文档还是 A，
         * 更新 0 行却回"编辑成功"（评审发现的静默失败）。
         * 条件里同时带归属，等于把"能不能改"交给数据库执行，是越权校验之外的第二道闸。
         */
        QueryWrapper<Object> where = new QueryWrapper<>().eq("_id", obj.get("_id"));
        if (StringUtils.isNotBlank(whereOrgCode)) {
            where.eq(SYS_ORG_CODE, whereOrgCode);
        }
        mongoMapper.update(COLLECTION, obj, where);

        /*
         * mongoplus 的实体更新会**跳过 null 字段**，但"清空"是有意义的操作：
         *   删掉跳转链接、移除某个语言的图片或标题。
         * 实测过：不显式 unset 的话，"清空链接"会静默失败（评审修复项 R2 就是这条）。
         */
        UpdateWrapper<Object> clear = new UpdateWrapper<>().eq("_id", obj.get("_id"));
        if (StringUtils.isNotBlank(whereOrgCode)) {
            clear.eq(SYS_ORG_CODE, whereOrgCode);
        }
        boolean needUnset = false;
        if (params.containsKey("targetUrl") && StringUtils.isEmpty(params.getString("targetUrl"))) {
            clear.unset("targetUrl");
            needUnset = true;
        }
        for (LangKeys keys : LangKeys.getUsingLangKeys(lanMapper.using())) {
            JSONObject langObj = params.getJSONObject(keys.langCode);
            if (langObj == null) {
                continue;
            }
            if (langObj.containsKey(keys.picUrlKey) && StringUtils.isBlank(langObj.getString(keys.picUrlKey))) {
                clear.unset(keys.picUrlKey);
                needUnset = true;
            }
            if (langObj.containsKey(keys.titleKey) && StringUtils.isBlank(langObj.getString(keys.titleKey))) {
                clear.unset(keys.titleKey);
                needUnset = true;
            }
        }
        if (needUnset) {
            mongoMapper.update(COLLECTION, clear);
        }
    }

    @Override
    public void replaceAll(List<Map<String, Object>> params, String orgCode) {
        // 只清掉"本店"的轮播图：以前这里是 exists("_id", true)，会把所有店铺的配置一起抹掉
        UpdateWrapper<Object> scope = new UpdateWrapper<>();
        if (StringUtils.isNotBlank(orgCode)) {
            scope.eq(SYS_ORG_CODE, orgCode);
        } else {
            scope.exists("_id", true);
        }
        mongoMapper.remove(COLLECTION, scope);

        if (params != null && !params.isEmpty()) {
            for (Map<String, Object> item : params) {
                if (item != null && StringUtils.isNotBlank(orgCode)) {
                    item.put(SYS_ORG_CODE, orgCode);
                }
            }
            mongoMapper.saveBatch(COLLECTION, params);
        }
    }

    @Override
    public void removeSlideshow(List<String> ids, String orgCode) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 逐条按 _id 删除：mongoplus 会将字符串主键转换成 ObjectId 参与匹配
        for (String id : ids) {
            if (StringUtils.isBlank(id)) {
                continue;
            }
            UpdateWrapper<Object> where = new UpdateWrapper<>().eq("_id", id);
            if (StringUtils.isNotBlank(orgCode)) {
                where.eq(SYS_ORG_CODE, orgCode);
            }
            mongoMapper.remove(COLLECTION, where);
        }
    }

    @Override
    public void updateSlideshowSort(List<Map<String, Object>> sortList, String orgCode) {
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
            UpdateWrapper<Object> where = new UpdateWrapper<>().eq("_id", id.toString());
            if (StringUtils.isNotBlank(orgCode)) {
                where.eq(SYS_ORG_CODE, orgCode);
            }
            mongoMapper.update(COLLECTION, where.set(SORT_FIELD, sort));
        }
    }

    @Override
    public List<Map<String, Object>> listSlideshow(String language, String orgCode) {
        // 解析不到店铺归属时报空，而不是"不过滤"——后者会把别店轮播图放到前台
        if (StringUtils.isBlank(orgCode)) {
            return Collections.emptyList();
        }
        List<LangKeys> langKeysList = LangKeys.getUsingLangKeys(lanMapper.using());

        /*
         * 投影**所有语言**的标题/图片，再在 Java 里做兜底。
         * 以前只投影"目标语言 + en"，兜底链也就是"目标语言 → en"：
         * 某店只配了中文素材、而访客 locale 解析成 en 时，title/pic_url 全是 null，
         * 首页横幅会整块没有图（评审指出的健壮性问题）。
         */
        List<Projection> projections = new ArrayList<>();
        projections.add(new Projection("_id", 1));
        projections.add(new Projection(SORT_FIELD, 1));
        projections.add(new Projection("goods", 1));
        projections.add(new Projection("targetUrl", 1));
        for (LangKeys keys : langKeysList) {
            projections.add(new Projection(keys.picUrlKey, 1));
            projections.add(new Projection(keys.titleKey, 1));
        }

        QueryWrapper<Object> qw = new QueryWrapper<>()
                .project(projections.toArray(new Projection[0]))
                .eq("enabled", "Y")
                .eq(SYS_ORG_CODE, orgCode);

        List<Map<String, Object>> list = mongoMapper.list(COLLECTION, qw, new TypeReference<>() {});

        for (Map<String, Object> map : list) {
            map.put("title", firstNonBlank(map, langKeysList, language, "title"));
            map.put("pic_url", firstNonBlank(map, langKeysList, language, "pic_url"));
            // 中间字段不暴露给前端（前端只认 title / pic_url）
            for (LangKeys keys : langKeysList) {
                map.remove(keys.titleKey);
                map.remove(keys.picUrlKey);
            }
            map.remove("title_en");
            map.remove("pic_url_en");
            if (map.get("_id") != null) {
                map.put("_id", map.get("_id").toString());
            }
        }

        /*
         * 按 sort 升序：后台拖拽排序保存的就是 sort 字段。
         * 以前这里既不投影 sort 也不排序，前台顺序 = Mongo 自然顺序，后台排序形同虚设。
         */
        list.sort(Comparator.comparingInt(m -> toSortValue(m.get(SORT_FIELD))));
        return list;
    }

    /**
     * 获取所有轮播图（以language分组）。
     *
     * @param orgCode 只看某个归属部门；为空表示不限（总部总览）
     */
    @Override
    public JSONArray getSlideshowGroupByLang(String orgCode) {
        QueryWrapper<Object> qw = new QueryWrapper<>();
        if (StringUtils.isNotBlank(orgCode)) {
            qw.eq(SYS_ORG_CODE, orgCode);
        }
        List<Map<String, Object>> all = mongoMapper.list(COLLECTION, qw, new TypeReference<>() {});

        if (all == null || all.isEmpty()) {
            return new JSONArray();
        }

        // 1. 预先构建各语言对应的 key 配置，将字符串拼接移出内层循环
        List<SysLanguage> usingLang = lanMapper.using();
        List<LangKeys> langKeysList = LangKeys.getUsingLangKeys(usingLang);

        // 2. 按 sort 升序，让后台列表与前台顺序一致（拖拽排序后刷新也不会跳来跳去）
        all.sort(Comparator.comparingInt(m -> toSortValue(m.get(SORT_FIELD))));

        // 3. 初始化结果数组
        JSONArray resultArray = new JSONArray();

        // 4. 遍历数据组装 JSON
        for (Map<String, Object> item : all) {
            JSONObject slideObj = new JSONObject();

            // 填充公共属性
            slideObj.put("id", item.get("_id").toString());
            slideObj.put("desc", item.get("desc"));
            slideObj.put("sort", item.get(SORT_FIELD));
            slideObj.put("goods", item.get("goods"));
            // 归属部门：前端按店铺管理时据此校验/展示
            slideObj.put("sysOrgCode", item.get(SYS_ORG_CODE));
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

    @Override
    public Map<String, Object> getSlideshow(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        List<Map<String, Object>> list = mongoMapper.list(COLLECTION,
                new QueryWrapper<>().eq("_id", id), new TypeReference<>() {});
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Override
    public String findOrgCodeById(String id) {
        Map<String, Object> doc = getSlideshow(id);
        if (doc == null) {
            return null;
        }
        Object orgCode = doc.get(SYS_ORG_CODE);
        return orgCode == null ? null : orgCode.toString();
    }

    /**
     * 组装轮播图主体字段（不含归属，归属由各方法自行写入）。
     */
    private Map<String, Object> buildSlideBody(JSONObject params) {
        List<SysLanguage> usingLang = lanMapper.using();
        List<LangKeys> langKeysList = LangKeys.getUsingLangKeys(usingLang);

        Map<String, Object> obj = new HashMap<>();
        obj.put(SORT_FIELD, params.getInteger("sort"));
        obj.put("enabled", params.getString("enabled"));
        obj.put("desc", params.getString("desc"));
        obj.put("goods", params.getString("goods"));

        /*
         * targetUrl 用 containsKey 区分"没提交"与"显式清空"：
         *   不传该键      → 不动它（其它调用方只改排序时不会被清掉链接）
         *   传 ""         → 清空链接（null）
         *   传非空        → 自动补协议
         * 以前只在非空时 put、空串直接忽略，导致"链接改不掉也删不掉"（评审实测确认）。
         */
        if (params.containsKey("targetUrl")) {
            String targetUrl = params.getString("targetUrl");
            if (StringUtils.isEmpty(targetUrl)) {
                obj.put("targetUrl", null);
            } else {
                obj.put("targetUrl", (targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))
                        ? targetUrl : "http://" + targetUrl);
            }
        }

        for (LangKeys langKeys : langKeysList) {
            JSONObject langObj = params.getJSONObject(langKeys.langCode);
            obj.put(langKeys.picUrlKey, langObj == null ? null : langObj.getString(langKeys.picUrlKey));
            obj.put(langKeys.titleKey, langObj == null ? null : langObj.getString(langKeys.titleKey));
        }
        return obj;
    }

    /**
     * 多语言取值兜底：目标语言 → zh → en → 任一非空。
     */
    private String firstNonBlank(Map<String, Object> doc, List<LangKeys> langKeysList, String language, String kind) {
        List<String> candidates = new ArrayList<>();
        for (LangKeys keys : langKeysList) {
            boolean hit = StringUtils.isNotBlank(language) && language.toLowerCase().startsWith(keys.langCode.toLowerCase());
            if (hit) {
                candidates.add("title".equals(kind) ? keys.titleKey : keys.picUrlKey);
            }
        }
        candidates.add("title".equals(kind) ? "title_zh" : "pic_url_zh");
        candidates.add("title".equals(kind) ? "title_en" : "pic_url_en");
        for (LangKeys keys : langKeysList) {
            String key = "title".equals(kind) ? keys.titleKey : keys.picUrlKey;
            if (!candidates.contains(key)) {
                candidates.add(key);
            }
        }
        for (String key : candidates) {
            Object value = doc.get(key);
            if (value != null && StringUtils.isNotBlank(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private int toSortValue(Object value) {
        if (value == null) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
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
