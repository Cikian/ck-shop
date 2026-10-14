package org.jeecg.modules.shop.promotion.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.shop.promotion.entity.vo.PromoBriefVO;
import org.jeecg.modules.shop.promotion.service.IShopPromotionService;
import org.jeecg.modules.shop.store.StoreResolver;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 店铺端（前台）促销活动
 * 只暴露**当前店铺**正在进行中的促销活动，无需登录即可访问。
 * </p>
 *
 * <b>路径约定：</b>{@code /shop/promotion/store/active}。
 * 原先是 {@code /shop/promotion/active}，与后台 CRUD 同在前缀 {@code /shop/promotion} 下，
 * 匿名接口和后台接口混在一起；现按既有约定
 * （{@code /shop/product/list} 后台 vs {@code /shop/product/store/list} 店铺端）
 * 统一挪到 {@code /store} 子路径下。
 *
 * <b>店铺身份</b>的解析方式与 {@code ShopStoreProductController} / {@code ShopStoreCategoryController}
 * **完全一致**：统一走 {@link StoreResolver}——请求头 {@code X-Store-Code} 优先
 * （由前台站点服务端注入，不出现在用户可见 URL 中），其次按 Host 域名兜底；
 * 解析不到就报错，**绝不返回全量活动**。
 *
 * <b>与商品/分类的区别：</b>促销活动的归属列是 {@code ck_shop_promotion.store_id}
 * （直接指向 {@code ck_shop_store.id}），不是部门编码，所以这里取店铺 id 过滤，
 * 不需要再过一层 {@code ShopOrgScopeResolver} 的店铺 → org_code 转换。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-13
 */

@Tag(name = "店铺端-促销活动")
@RestController
@RequestMapping("/shop/promotion/store")
@Slf4j
public class ShopStorePromotionController {

    /** 默认语言：多语言全部缺失时前台必须还能渲染，兜底中文 */
    private static final String DEFAULT_LANGUAGE = "zh";

    @Autowired
    private IShopPromotionService shopPromotionService;

    @Autowired
    private StoreResolver storeResolver;

    /**
     * 进行中的促销活动列表（当前店铺，**多条**，最多 10 条）
     *
     * <p>返回多条而不是单条：首页秒杀条需要轮流播放，只给第一条的话其余活动等于白配。
     * 排序 {@code sort_order ASC, end_time ASC}。
     *
     * <p><b>关于 lang：</b>活动标题/副标题支持多语言（zh/en/fr），
     * 解析顺序为 {@code translation[lang] → translation['zh'] → 主表}；
     * 主表那两列是兜底文案，历史数据没有翻译行时仍能正常展示。
     *
     * @param lang 语言标识（zh/en/fr），缺省按 zh
     */
    @IgnoreAuth
    @Operation(summary = "店铺端-进行中的促销活动(多条)")
    @GetMapping(value = "/active")
    public Result<List<PromoBriefVO>> active(@RequestParam(name = "lang", required = false) String lang,
                                             HttpServletRequest req) {
        ShopStore store = storeResolver.resolve(req);
        if (store == null || StringUtils.isBlank(store.getId())) {
            // 与商品/分类接口的降级策略一致：店铺归属缺失属于数据缺失，
            // 返回空列表而不是 500，整站仍可访问（解析失败时 StoreResolver 已抛错并打日志）
            log.warn("店铺解析结果为空或缺少店铺 id，促销活动接口返回空列表");
            return Result.OK(new ArrayList<PromoBriefVO>());
        }
        String language = StringUtils.isBlank(lang) ? DEFAULT_LANGUAGE : lang;
        return Result.OK(shopPromotionService.listActiveByStore(store.getId(), language));
    }
}
