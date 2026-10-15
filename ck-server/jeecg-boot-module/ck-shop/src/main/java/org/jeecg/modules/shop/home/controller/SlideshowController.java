package org.jeecg.modules.shop.home.controller;


import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.shop.home.service.CommonMongoService;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.jeecg.modules.shop.store.StoreResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 首页轮播图（分店铺管理）
 * </p>
 *
 * <b>隔离维度：</b>轮播图文档带 {@code sys_org_code} 归属列，与商品/分类同一套规则
 * （店铺 ↔ 部门 1:1）。
 * <ul>
 *   <li><b>店铺端</b> {@code GET /home/slide}：由 {@link StoreResolver} 解出店铺，
 *       再取店铺绑定部门的 org_code；解析不到一律报错，绝不回退成"全局轮播图"；</li>
 *   <li><b>管理端</b>：运营只能读写本部门；{@code shop-store:manage-all}（总部/管理员）
 *       可跨店铺，通过 {@code sysOrgCode} 指定目标店铺。</li>
 * </ul>
 *
 * 归属用等值匹配（不是前缀匹配）：轮播图是"每店一份配置"的文档型数据，
 * 不存在"本部门及以下"的下钻语义，等值语义更贴近直觉也更不容易串店。
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026/7/20
 */

@RestController
@RequestMapping("/home/slide")
@Tag(name = "首页轮播图")
public class SlideshowController {

    /** 归属列名，与 CommonMongoServiceImpl.SYS_ORG_CODE 保持一致 */
    private static final String SYS_ORG_CODE = "sys_org_code";

    @Autowired
    private CommonMongoService mongoService;

    @Autowired
    private StoreResolver storeResolver;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    // ==================================================================
    // 管理端（需登录）
    // ==================================================================

    @AutoLog(value = "首页轮播图-添加")
    @RequiresPermissions("ck:shop:slideshow:add")
    @PostMapping
    @Operation(summary = "保存轮播图")
    public Result<?> save(@RequestBody JSONObject params) {
        // 归属：运营=本人部门；管理员可指定 sysOrgCode，越权/未绑定店铺都会被拒
        String orgCode = orgScopeResolver.resolveWritableOrgCode(params.getString("sysOrgCode"), null);
        String id = mongoService.saveSlideshow(params, orgCode);
        // **必须把新主键回给前端**：前端"填完即自动保存"，拿不到 id 会把同一条草稿反复 POST 成多条
        return Result.OK("保存成功", id);
    }

    @AutoLog(value = "首页轮播图-编辑")
    @RequiresPermissions("ck:shop:slideshow:edit")
    @PutMapping
    @Operation(summary = "编辑轮播图（update one）")
    public Result<?> edit(@RequestBody JSONObject params) {
        String id = params.getString("id");
        if (StringUtils.isBlank(id)) {
            return Result.error("轮播图主键不能为空");
        }
        Map<String, Object> exist = mongoService.getSlideshow(id);
        if (exist == null) {
            return Result.error("轮播图不存在或已删除");
        }
        String existOrgCode = asString(exist.get(SYS_ORG_CODE));
        // 越权防线：先确认这条轮播图在本人可见范围内（接口可被直接按 id 调用）
        orgScopeResolver.checkAccessible(existOrgCode, "轮播图");
        String orgCode = orgScopeResolver.resolveWritableOrgCode(params.getString("sysOrgCode"), existOrgCode);
        // 匹配用现值、写入用目标值：管理员迁移归属时不会"报成功却没改"
        mongoService.editSlideshow(params, existOrgCode, orgCode);
        return Result.OK("编辑成功");
    }

    @AutoLog(value = "首页轮播图-全量替换")
    @RequiresPermissions("ck:shop:slideshow:edit")
    @PostMapping("/all")
    @Operation(summary = "全量替换轮播图")
    public Result<?> replaceAll(@RequestBody List<Map<String, Object>> params,
                                @RequestParam(name = "sysOrgCode", required = false) String sysOrgCode) {
        String orgCode = orgScopeResolver.resolveWritableOrgCode(sysOrgCode, null);
        // 只替换本店的轮播图，不会像以前那样把所有店铺的配置一起抹掉
        mongoService.replaceAll(params, orgCode);
        return Result.OK("保存成功");
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除轮播图（单条）")
    @RequiresPermissions("ck:shop:slideshow:delete")
    public Result<?> delete(@RequestParam(name = "id") String id) {
        if (StringUtils.isBlank(id)) {
            return Result.error("轮播图主键不能为空");
        }
        Map<String, Object> exist = mongoService.getSlideshow(id);
        if (exist == null) {
            return Result.error("轮播图不存在或已删除");
        }
        String existOrgCode = asString(exist.get(SYS_ORG_CODE));
        orgScopeResolver.checkAccessible(existOrgCode, "轮播图");
        mongoService.removeSlideshow(Collections.singletonList(id), existOrgCode);
        return Result.OK("删除成功");
    }

    @DeleteMapping("/deleteBatch")
    @Operation(summary = "删除轮播图（批量）")
    @RequiresPermissions("ck:shop:slideshow:deleteBatch")
    public Result<?> deleteBatch(@RequestParam(name = "ids") String ids) {
        if (StringUtils.isBlank(ids)) {
            return Result.error("请选择要删除的轮播图");
        }
        List<String> idList = Arrays.asList(ids.split(","));
        // 逐条确认归属后按归属分组删除：绝不出现"按 id 删掉了别店的轮播图"
        Map<String, List<String>> idsByOrg = new LinkedHashMap<>();
        for (String id : idList) {
            if (StringUtils.isBlank(id)) {
                continue;
            }
            Map<String, Object> exist = mongoService.getSlideshow(id);
            if (exist == null) {
                return Result.error("轮播图不存在或已删除：" + id);
            }
            String existOrgCode = asString(exist.get(SYS_ORG_CODE));
            orgScopeResolver.checkAccessible(existOrgCode, "轮播图");
            idsByOrg.computeIfAbsent(existOrgCode == null ? "" : existOrgCode, k -> new ArrayList<>()).add(id);
        }
        idsByOrg.forEach((orgCode, grouped) -> mongoService.removeSlideshow(grouped, orgCode));
        return Result.OK("删除成功");
    }

    @PutMapping("/sort")
    @Operation(summary = "保存轮播图排序")
    @RequiresPermissions("ck:shop:slideshow:edit")
    public Result<?> saveSort(@RequestBody List<Map<String, Object>> sortList) {
        if (sortList == null || sortList.isEmpty()) {
            return Result.OK("排序已保存");
        }
        Map<String, List<Map<String, Object>>> byOrg = new LinkedHashMap<>();
        for (Map<String, Object> item : sortList) {
            Object id = item == null ? null : item.get("id");
            if (id == null) {
                continue;
            }
            Map<String, Object> exist = mongoService.getSlideshow(id.toString());
            if (exist == null) {
                return Result.error("轮播图不存在或已删除：" + id);
            }
            String existOrgCode = asString(exist.get(SYS_ORG_CODE));
            orgScopeResolver.checkAccessible(existOrgCode, "轮播图");
            byOrg.computeIfAbsent(existOrgCode == null ? "" : existOrgCode, k -> new ArrayList<>()).add(item);
        }
        // 空 key 表示历史遗留的"无归属"文档：能走到这里说明调用方已通过越权校验（仅管理员）
        byOrg.forEach((orgCode, grouped) -> mongoService.updateSlideshowSort(grouped, orgCode));
        return Result.OK("排序已保存");
    }

    /**
     * 管理端：按语言分组返回轮播图。
     *
     * @param sysOrgCode 目标店铺的归属部门编码；不传时——总部看全部，运营只看本部门
     */
    @GetMapping("/all")
    @Operation(summary = "获取所有轮播图（以language分组）")
    public Result<?> listAll(@RequestParam(name = "sysOrgCode", required = false) String sysOrgCode) {
        String requested = StringUtils.trimToNull(sysOrgCode);
        // 运营 → 本人部门；总部/管理员 → null（不限）
        List<String> visible = orgScopeResolver.resolveVisibleOrgCodes();
        String effective;
        if (requested != null) {
            if (!orgScopeResolver.inScope(requested, visible)) {
                throw new JeecgBootException("无权查看该店铺的轮播图");
            }
            effective = requested;
        } else if (visible == null) {
            effective = null;
        } else {
            // 运营不传店铺参数时也必须限定到自己的部门，不能"不过滤"
            effective = visible.get(0);
        }
        JSONArray maps = mongoService.getSlideshowGroupByLang(effective);
        return Result.OK(maps);
    }

    // ==================================================================
    // 店铺端（前台，免登录）
    // ==================================================================

    /**
     * 店铺端：只返回**当前店铺**启用中的轮播图。
     *
     * <p>注意路径为什么是 {@code /home/slide/store} 而不是直接 {@code /home/slide}：
     * JeecgBoot 的 {@code @IgnoreAuth} 由 {@code IgnoreAuthPostProcessor} 在启动时扫描
     * <b>方法级 mapping 的 value()</b> 生成免登录 URL 表；{@code @GetMapping} 不写路径时
     * value() 是空数组，扫描结果为空 —— 注解形同没加。
     * 而 Shiro 里按 {@code /home/slide} 做路径级 anon 又会把 POST/PUT 一起放行（匿名可写）。
     * 因此这里给"店铺端只读"一个独立路径，写接口仍旧一律要求登录。
     */
    @IgnoreAuth
    @GetMapping("/store")
    @Operation(summary = "获取对应语言的轮播图（店铺端）")
    public Result<?> storeList(HttpServletRequest request) {
        String lang = resolveLanguage(request);
        // 店铺 → 店铺绑定部门 → 轮播图归属；解析失败直接报错，绝不返回别的店铺的轮播图
        String orgCode = orgScopeResolver.resolveStoreOrgCodeOrNull(storeResolver.resolve(request));
        if (orgCode == null) {
            // 店铺没绑部门属于数据缺失：返回空轮播（首页还能打开），日志里已 warn
            return Result.OK(new ArrayList<Map<String, Object>>());
        }
        List<Map<String, Object>> maps = mongoService.listSlideshow(lang, orgCode);
        return Result.OK(maps);
    }

    /**
     * 语言解析：优先 x-language，其次 Accept-Language，兜底 en（与改造前保持一致）
     */
    private String resolveLanguage(HttpServletRequest request) {
        String lang = request.getHeader("x-language");
        if (StringUtils.isBlank(lang)) {
            lang = request.getHeader("Accept-Language");
        }
        if (StringUtils.isBlank(lang)) {
            return "en";
        }
        if (lang.contains(",")) {
            lang = lang.split(",")[0];
        }
        return lang.trim().toLowerCase();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
