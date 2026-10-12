package org.jeecg.modules.shop.home.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 通用无实体类service（首页轮播图）
 * </p>
 *
 * <b>分店铺管理（2026-10-12 补充）：</b>
 * 轮播图文档带归属列 {@code sys_org_code}，与商品/分类同一套隔离维度
 * （店铺 ↔ 部门 1:1，取店铺绑定部门的 org_code）。因此所有方法都要求调用方传入
 * {@code orgCode}，由 controller 负责解析与越权校验：
 * <ul>
 *   <li>店铺端读：店铺 → 绑定部门 org_code → 只返回该店的轮播图；</li>
 *   <li>管理端读写：运营只能操作本部门，管理员（{@code shop-store:manage-all}）可跨店。</li>
 * </ul>
 * 归属用**等值**匹配而不是前缀匹配：轮播图是"每店一份配置"这类文档，
 * 不是可下钻的业务数据；等值语义下"谁写的谁能读"，不会出现前缀把别店配置带出来的情况。
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026/7/20
 */
public interface CommonMongoService {

    /**
     * 新增一条轮播图。
     *
     * @return 新文档的 `_id`（前端"新增后自动保存"必须拿回主键，否则会把草稿丢掉、
     *         或者被当成新记录反复创建）
     */
    String saveSlideshow(JSONObject params, String orgCode);

    /**
     * 编辑一条轮播图。
     *
     * @param whereOrgCode 匹配条件里的归属（= **库里现值**，保证不会改到别店的文档）
     * @param setOrgCode   写入的归属（管理员可把它迁移到别的店铺；运营侧已被上层拦住）
     */
    void editSlideshow(JSONObject params, String whereOrgCode, String setOrgCode);

    void replaceAll(List<Map<String, Object>> params, String orgCode);

    /**
     * 根据 _id 删除轮播图。
     *
     * @param ids     轮播图主键集合（可为单个）
     * @param orgCode 归属部门编码；为空表示**不按归属过滤**，只允许在调用方已确认越权校验通过时使用
     *                （例如历史遗留的无归属文档，且调用方是管理员）
     */
    void removeSlideshow(List<String> ids, String orgCode);

    /**
     * 批量更新轮播图排序值（只改动 sort 字段，不影响图片、标题等其他数据）
     *
     * @param sortList [{id: 轮播图主键, sort: 排序值}]
     * @param orgCode  归属部门编码（同 removeSlideshow 的约定）
     */
    void updateSlideshowSort(List<Map<String, Object>> sortList, String orgCode);

    /**
     * 店铺端：取本店（本部门）启用中的轮播图。
     *
     * @param orgCode 店铺绑定部门的 org_code，必填；为空时返回空列表而不是全量
     */
    List<Map<String, Object>> listSlideshow(String language, String orgCode);

    /**
     * 管理端：按语言分组返回轮播图。
     *
     * @param orgCode 只看某个部门；为空表示**不限**（总部总览）
     */
    JSONArray getSlideshowGroupByLang(String orgCode);

    /**
     * 取单条轮播图（原始文档），用于编辑/删除前判定归属与越权。
     *
     * @return 文档不存在返回 {@code null}
     */
    Map<String, Object> getSlideshow(String id);

    /**
     * 取某条轮播图的归属部门编码。
     *
     * @return 文档不存在或未写入归属时返回 {@code null}
     */
    String findOrgCodeById(String id);
}
