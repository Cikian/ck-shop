package org.jeecg.modules.shop.promotion.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.promotion.entity.ShopPromotion;
import org.jeecg.modules.shop.promotion.entity.dto.PromotionDTO;
import org.jeecg.modules.shop.promotion.entity.dto.PromotionQuery;
import org.jeecg.modules.shop.promotion.entity.vo.PromoBriefVO;
import org.jeecg.modules.shop.promotion.entity.vo.PromotionConflictVO;
import org.jeecg.modules.shop.promotion.entity.vo.PromotionListVO;

import java.util.List;

/**
 * <p>
 * 促销活动 Service
 * </p>
 *
 * 管理端（后台 CRUD）与店铺端（前台只读）都在这里，
 * 原因是两者共用"活动 + 多语言 + 关联商品"这套读写逻辑，拆成两个 service
 * 只会让翻译/关联的写入规则出现两份实现，迟早分叉。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-13
 */
public interface IShopPromotionService extends IService<ShopPromotion> {

    // ==================================================================
    // 店铺端
    // ==================================================================

    /**
     * 店铺端：查询**当前进行中**的促销活动（按 sort_order、end_time 升序，最多 10 条）。
     *
     * <p>返回的是**多条**：首页秒杀条轮流播放，只给第一条的话其余活动等于白配。
     *
     * @param storeId 当前店铺 id（由 {@code StoreResolver} 从 X-Store-Code / Host 解析，**不可为空**）
     * @param lang    语言标识（zh/en/fr），用于解析 title/subTitle：
     *                {@code translation[lang] → translation['zh'] → 主表}
     * @return 活动简要信息；storeId 为空时返回空列表（fail-closed，绝不返回全量）
     */
    List<PromoBriefVO> listActiveByStore(String storeId, String lang);

    // ==================================================================
    // 管理端
    // ==================================================================

    /**
     * 管理端分页列表。
     *
     * <p>可见域规则：{@code storeId} 条件与"当前账号可见店铺"取交集，
     * 交集为空直接返回空分页——**绝不能因为筛不到就把条件丢掉**，
     * 那等于把别店的活动返回给调用方。
     *
     * @param query   业务筛选条件（storeId / 时间范围 / 高级查询开关等）
     * @param base    构造好的查询条件：由控制器用
     *                {@code QueryGenerator.initQueryWrapper(promotion, request.getParameterMap())} 生成，
     *                这样列表页的「高级查询」（superQueryParams）与字段级条件才不会静默失效；
     *                本方法会在此基础上追加店可见域与其它业务条件，
     *                并**最后**追加排序（排序必须在最后，否则会被后续 eq/like 打乱分段）
     */
    IPage<PromotionListVO> queryPageList(PromotionQuery query, QueryWrapper<ShopPromotion> base,
                                         Integer pageNo, Integer pageSize);

    /**
     * 详情：主表 + 多语言 + 关联商品，一次取全（编辑页只需一次请求）。
     */
    PromotionDTO getDetailById(String id);

    /**
     * 新增活动（主表 + 翻译 + 关联商品，同一事务）。
     *
     * @return **新活动 id**（契约要求 result 恒为活动 id，前端"保存并继续编辑"据此回填）
     */
    String savePromotion(PromotionDTO dto);

    /**
     * 编辑活动（主表 + 翻译 + 关联商品，同一事务）。
     *
     * @return **活动 id**
     */
    String updatePromotion(PromotionDTO dto);

    /**
     * 复制活动：新 id、新 code、复制翻译与关联商品；
     * **时间窗原样复制**，`status` 强制置为 `N`，`title` 加「(副本)」后缀。
     *
     * @param newCode 可空；为空时用 {@code 原code-COPY-yyyyMMddHHmmss}
     * @return 新活动 id
     */
    String copyPromotion(String id, String newCode);

    /**
     * 软删除（主表 del_flag=1），同时物理清理翻译表与关联表。
     */
    boolean removePromotion(String id);

    /**
     * 批量软删除；逐条校验可见域，有 id 不存在就整体失败（不做"部分成功"）。
     */
    boolean removePromotions(List<String> ids);

    /**
     * 上下线快捷开关。
     */
    boolean updateStatus(String id, String status);

    /**
     * 冲突检测：这些商品在 [startTime, endTime] 内是否已被别的活动占用。
     *
     * @param productIds 逗号分隔的商品 id（可空 → 返回空结果）
     * @param excludeId  要排除的活动 id（编辑场景传自己，否则会跟自己冲突）
     */
    List<PromotionConflictVO> checkConflict(String productIds, String startTime, String endTime, String excludeId);

    /**
     * 当前账号可见的店铺 id 列表。
     *
     * <p>导出接口与列表必须用**同一套**可见域，否则会出现"列表只有 3 条、导出却是一整张表"。
     *
     * @return {@code null} 表示不限（总部/管理员）；空列表表示没有任何可见店铺
     */
    List<String> resolveVisibleStoreIds();
}
