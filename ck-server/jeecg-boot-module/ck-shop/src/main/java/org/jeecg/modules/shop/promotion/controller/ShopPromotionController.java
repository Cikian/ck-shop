package org.jeecg.modules.shop.promotion.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.modules.shop.common.ShopSqlUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.shop.common.ShopSqlUtils;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.shop.common.ShopSqlUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.common.ShopSqlUtils;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.modules.shop.common.ShopSqlUtils;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.shop.common.ShopSqlUtils;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.modules.shop.promotion.entity.ShopPromotion;
import org.jeecg.modules.shop.promotion.entity.ShopPromotionTranslation;
import org.jeecg.modules.shop.promotion.entity.dto.PromotionDTO;
import org.jeecg.modules.shop.promotion.entity.dto.PromotionImportVO;
import org.jeecg.modules.shop.promotion.entity.dto.PromotionQuery;
import org.jeecg.modules.shop.promotion.entity.vo.PromotionConflictVO;
import org.jeecg.modules.shop.promotion.entity.vo.PromotionListVO;
import org.jeecg.modules.shop.promotion.service.IShopPromotionService;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.jeecg.modules.shop.store.mapper.ShopStoreMapper;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 促销活动管理（管理端）
 * </p>
 *
 * <b>与店铺端的分工：</b>本控制器是**后台**接口（需登录 + 权限码），
 * 前缀 {@code /shop/promotion}；前台免登录接口在
 * {@link ShopStorePromotionController}，前缀 {@code /shop/promotion/store}——
 * 与 {@code /shop/product/list} vs {@code /shop/product/store/list} 的约定一致，
 * 避免匿名接口和后台 CRUD 混在同一前缀下。
 *
 * <b>返回契约：</b>{@code add} / {@code edit} / {@code copy} 的 {@code result}
 * **恒为活动 id**，绝不放提示语——前端「保存并继续编辑」要据此回填表单。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Tag(name = "促销活动管理")
@RestController
@RequestMapping("/shop/promotion")
@Slf4j
public class ShopPromotionController extends JeecgController<ShopPromotion, IShopPromotionService> {

    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Autowired
    private IShopPromotionService shopPromotionService;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    @Autowired
    private ShopStoreMapper shopStoreMapper;

    /** 导出时定位上传根目录（与 ShopExportSupport 取的是同一处配置） */
    @Autowired
    private JeecgBaseConfig jeecgBaseConfig;

    /**
     * 分页列表查询（带数据可见域 + 高级查询）
     *
     * <p>参数分两路，各司其职：
     * <ul>
     *   <li>{@code promotion}（绑定普通字段如 code/type/status/startTime_begin…）+
     *       {@code request.getParameterMap()} → 交给 {@code QueryGenerator}，
     *       这样列表页的「高级查询」（{@code superQueryParams} / {@code superQueryMatchType}）
     *       才真正生效。缺了它，用户点高级查询会"看起来能用但完全没过滤"，是最难发现的假控件；</li>
     *   <li>{@code query}（{@link PromotionQuery}：storeId 数据权限与时间区间）→ 由服务层追加。</li>
     * </ul>
     */
    @Operation(summary = "促销活动-分页列表查询")
    @RequiresPermissions("shop-promotion:list")
    @GetMapping(value = "/list")
    public Result<IPage<PromotionListVO>> queryPageList(PromotionQuery query, ShopPromotion promotion,
                                                       @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                       @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                       HttpServletRequest req) {
        QueryWrapper<ShopPromotion> wrapper =
                QueryGenerator.initQueryWrapper(promotion, excludeBusinessParams(req.getParameterMap()));
        return Result.OK(shopPromotionService.queryPageList(query, wrapper, pageNo, pageSize));
    }

    /**
     * 从参数表里摘掉**由 {@link PromotionQuery} 负责**的固定参数名，再交给 {@code QueryGenerator}。
     *
     * <p>为什么必须摘：这些参数名与 {@code ShopPromotion} 的实体字段同名，
     * {@code QueryGenerator} 会把它们当成"实体条件"再拼一遍。而且同名字段的**语义不同**——
     * 契约要求 {@code code} / {@code title} 是**模糊**匹配，而 QueryGenerator 对无通配符的字符串走
     * {@code EQ}（精确匹配）。两份条件相与，最终等价于精确匹配，运营在"活动编码"里输入片段就永远搜不到，
     * 属于"看起来能用、实际静默失效"的假控件。
     *
     * <p>只摘固定名字；{@code superQueryParams} / {@code superQueryMatchType}（高级查询）
     * 与其余实体字段条件保持原样。
     */
    private Map<String, String[]> excludeBusinessParams(Map<String, String[]> parameterMap) {
        if (parameterMap == null || parameterMap.isEmpty()) {
            return parameterMap;
        }
        Map<String, String[]> filtered = new HashMap<>(parameterMap);
        // 前 4 个：与实体同名的业务筛选（模糊/区间语义由 PromotionQuery 承担）
        // 后 2 个：PromotionQuery 独有的时间区间名，QueryGenerator 不认识，留着只会白跑一遍
        for (String name : new String[]{"code", "title", "type", "status",
                "startTimeBegin", "startTimeEnd", "endTimeBegin", "endTimeEnd"}) {
            filtered.remove(name);
        }
        return filtered;
    }

    /**
     * 通过id查询（编辑页一次性取全：主表 + 多语言 + 关联商品）
     */
    @Operation(summary = "促销活动-通过id查询")
    @RequiresPermissions("shop-promotion:list")
    @GetMapping(value = "/queryById")
    public Result<PromotionDTO> queryById(@RequestParam(name = "id", required = true) String id) {
        return Result.OK(shopPromotionService.getDetailById(id));
    }

    /**
     * 添加
     */
    @AutoLog(value = "促销活动-添加")
    @Operation(summary = "促销活动-添加")
    @RequiresPermissions("shop-promotion:add")
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody PromotionDTO promotion) {
        // 返回新活动ID：前端"保存并继续编辑"要据此回填，不能只回一句提示语
        return Result.OK("添加成功！", shopPromotionService.savePromotion(promotion));
    }

    /**
     * 编辑
     */
    @AutoLog(value = "促销活动-编辑")
    @Operation(summary = "促销活动-编辑")
    @RequiresPermissions("shop-promotion:edit")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<String> edit(@RequestBody PromotionDTO promotion) {
        // 与新增保持一致的返回契约：result 恒为活动ID，绝不放提示语
        return Result.OK("编辑成功!", shopPromotionService.updatePromotion(promotion));
    }

    /**
     * 上下线快捷开关
     */
    @AutoLog(value = "促销活动-上下线")
    @Operation(summary = "促销活动-上下线")
    @RequiresPermissions("shop-promotion:edit")
    @RequestMapping(value = "/updateStatus", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<String> updateStatus(@RequestParam(name = "id") String id,
                                       @RequestParam(name = "status") String status) {
        shopPromotionService.updateStatus(id, status);
        return Result.OK("Y".equals(status) ? "上线成功!" : "下线成功!");
    }

    /**
     * 批量上线/下线（清单 A15）：逐条复用 updateStatus 的对象级授权，
     * 任一条失败整体回滚。
     */
    @AutoLog(value = "促销活动-批量上下线")
    @Operation(summary = "促销活动-批量上下线")
    @RequiresPermissions("shop-promotion:edit")
    @PostMapping(value = "/updateStatusBatch")
    public Result<String> updateStatusBatch(@RequestParam(name = "ids") String ids,
                                            @RequestParam(name = "status") String status) {
        int updated = shopPromotionService.updateStatusBatch(Arrays.asList(ids.split(",")), status);
        return Result.OK("批量" + ("Y".equals(status) ? "上线" : "下线") + "成功，共 " + updated + " 个活动");
    }

    /**
     * 复制活动（新 id / 新 code，status 强制停用，复制翻译与关联商品）
     */
    @AutoLog(value = "促销活动-复制")
    @Operation(summary = "促销活动-复制")
    @RequiresPermissions("shop-promotion:copy")
    @PostMapping(value = "/copy")
    public Result<String> copy(@RequestParam(name = "id") String id,
                               @RequestParam(name = "newCode", required = false) String newCode) {
        // 同样恒返回活动ID：前端复制后一般会直接跳到新活动的编辑页
        return Result.OK("复制成功！", shopPromotionService.copyPromotion(id, newCode));
    }

    /**
     * 通过id删除（软删除主表 + 清理翻译与关联）
     */
    @AutoLog(value = "促销活动-通过id删除")
    @Operation(summary = "促销活动-通过id删除")
    @RequiresPermissions("shop-promotion:delete")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id", required = true) String id) {
        shopPromotionService.removePromotion(id);
        return Result.OK("删除成功!");
    }

    /**
     * 批量删除（软删除；逐条校验可见域，有 id 不存在就整体失败）
     */
    @AutoLog(value = "促销活动-批量删除")
    @Operation(summary = "促销活动-批量删除")
    @RequiresPermissions("shop-promotion:deleteBatch")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        shopPromotionService.removePromotions(splitIds(ids));
        return Result.OK("批量删除成功!");
    }

    /**
     * 冲突检测：这些商品在该时间窗内是否已被别的活动占用
     */
    @Operation(summary = "促销活动-冲突检测")
    @RequiresPermissions("shop-promotion:checkConflict")
    @GetMapping(value = "/checkConflict")
    public Result<List<PromotionConflictVO>> checkConflict(
            @RequestParam(name = "productIds", required = false) String productIds,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "excludeId", required = false) String excludeId) {
        return Result.OK(shopPromotionService.checkConflict(productIds, startTime, endTime, excludeId));
    }

    /**
     * 导出excel（带数据可见域 + 与列表完全一致的筛选口径）
     *
     * <p>注 1：不能直接用 {@code super.exportXls} —— 它只用 {@code QueryGenerator} 拼条件，
     * 没有店铺可见域过滤，会把所有店铺的活动导出去（列表有可见域、导出却没有）。
     *
     * <p>注 2：为什么不用 {@code ShopExportSupport.export(...)}：它只接受"实体 + 参数表"，
     * 而列表的 {@code code}/{@code title} 是**模糊**匹配、实体绑定却是**精确**匹配，
     * 两者相与会让导出结果比列表少（"列表 5 条、导出 1 条"，运营完全无法理解）。
     * 因此这里手工组装导出视图（沿用 JeecgBoot 控制器里 ModelAndView + AutoPoi 的常规写法，
     * 原 {@code JeecgOrderMainController} 示例代码已从本模块移除），用与列表同一套 wrapper 取数。
     */
    @RequiresPermissions("shop-promotion:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, ShopPromotion shopPromotion,
                                  PromotionQuery query) {
        QueryWrapper<ShopPromotion> wrapper =
                QueryGenerator.initQueryWrapper(new ShopPromotion(), excludeBusinessParams(request.getParameterMap()));
        // 导出不分页，但必须与列表同样带上可见域；scope == null 表示不限（总部/管理员）
        List<String> scope = shopPromotionService.resolveVisibleStoreIds();
        if (scope != null) {
            if (scope.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in("store_id", scope);
            }
        }
        // 与列表一致：code/title 模糊、type/status 等值、时间区间
        applyPromotionQuery(wrapper, query, shopPromotion);
        // 与框架一致：勾选导出时只导选中的行
        String selections = request.getParameter("selections");
        if (StringUtils.isNotBlank(selections)) {
            wrapper.in("id", Arrays.asList(selections.split(",")));
        }

        List<ShopPromotion> exportList = shopPromotionService.list(wrapper);
        String exporter = "system";
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            if (principal instanceof LoginUser && StringUtils.isNotBlank(((LoginUser) principal).getRealname())) {
                exporter = ((LoginUser) principal).getRealname();
            }
        } catch (Exception ignored) {
            // 无 Shiro 上下文（例如本地直连调试）时忽略
        }

        ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
        mv.addObject(NormalExcelConstants.FILE_NAME, "促销活动");
        mv.addObject(NormalExcelConstants.CLASS, ShopPromotion.class);
        ExportParams exportParams =
                new ExportParams("促销活动报表", "导出人:" + exporter, "促销活动", ExcelType.XSSF);
        exportParams.setImageBasePath(jeecgBaseConfig.getPath().getUpload());
        mv.addObject(NormalExcelConstants.PARAMS, exportParams);
        mv.addObject(NormalExcelConstants.DATA_LIST, exportList);
        return mv;
    }

    /**
     * 把业务筛选条件写到 wrapper 上（导出用；列表的同类逻辑在 Service 里，
     * 这里只做"列表怎么筛、导出就怎么筛"的最小复刻）。
     */
    private void applyPromotionQuery(QueryWrapper<ShopPromotion> wrapper, PromotionQuery query,
                                     ShopPromotion entity) {
        String code = firstNonBlank(query == null ? null : query.getCode(),
                entity == null ? null : entity.getCode());
        String title = firstNonBlank(query == null ? null : query.getTitle(),
                entity == null ? null : entity.getTitle());
        String type = firstNonBlank(query == null ? null : query.getType(),
                entity == null ? null : entity.getType());
        String status = firstNonBlank(query == null ? null : query.getStatus(),
                entity == null ? null : entity.getStatus());
        wrapper.eq(StringUtils.isNotBlank(type), "type", type);
        wrapper.eq(StringUtils.isNotBlank(status), "status", status);
        if (StringUtils.isNotBlank(code)) {
            wrapper.like("code", ShopSqlUtils.escapeLike(code));
        }
        if (StringUtils.isNotBlank(title)) {
            String kw = ShopSqlUtils.escapeLike(title);
            wrapper.and(w -> w.like("title", kw).or().inSql("id",
                    "SELECT promotion_id FROM ck_shop_promotion_translation WHERE title LIKE '%" + kw + "%'"));
        }
        if (query != null) {
            appendDateRange(wrapper, "start_time", query.getStartTimeBegin(), query.getStartTimeEnd());
            appendDateRange(wrapper, "end_time", query.getEndTimeBegin(), query.getEndTimeEnd());
        }
    }

    private void appendDateRange(QueryWrapper<ShopPromotion> wrapper, String column, String begin, String end) {
        if (StringUtils.isNotBlank(begin)) {
            wrapper.ge(column, parseDateOrThrow(begin));
        }
        if (StringUtils.isNotBlank(end)) {
            wrapper.le(column, parseDateOrThrow(end));
        }
    }

    private Date parseDateOrThrow(String value) {
        SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN);
        format.setLenient(false);
        try {
            return format.parse(value.trim());
        } catch (ParseException e) {
            throw new JeecgBootException("时间格式不正确：" + value + "，应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }


    /**
     * 通过excel导入数据
     *
     * <p><b>导入范围（必须在页面上告知用户）：</b>只导入活动**主表字段**
     * （店铺编码、活动编码、类型、名称、时间窗、力度、状态）。
     * 「多语言明细」与「关联商品」**不参与导入**——Excel 单行无法表达多行明细，
     * 硬塞只会让用户误以为"商品也导进来了"。
     *
     * <p>唯一的例外是：Excel 里的「活动名称/副标题」会写入一条 **zh 翻译**
     * （多语言规则要求至少含 zh，不自动补的话每一行都会因"缺少中文文案"失败），
     * 其它语言需要导入后到编辑页补充。
     */
    @AutoLog(value = "促销活动-导入")
    @RequiresPermissions("shop-promotion:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        if (!(request instanceof MultipartHttpServletRequest)) {
            return Result.error("导入失败：请使用 multipart/form-data 上传 Excel");
        }
        List<String> failures = new ArrayList<>();
        int success = 0;
        int rowNo = 0;
        for (MultipartFile file : ((MultipartHttpServletRequest) request).getFileMap().values()) {
            ImportParams params = new ImportParams();
            // 与导出格式对齐：第 1 行大标题、第 2 行"导出人"，第 3 行才是表头
            params.setTitleRows(2);
            params.setHeadRows(1);
            try (java.io.InputStream in = file.getInputStream()) {
                List<PromotionImportVO> rows = ExcelImportUtil.importExcel(in, PromotionImportVO.class, params);
                if (rows == null || rows.isEmpty()) {
                    return Result.error("导入失败：没有解析到任何数据行");
                }
                for (PromotionImportVO row : rows) {
                    rowNo++;
                    // 逐行独立成败：一行格式不对不应该让整份文件白导（用户得重新整理 Excel）
                    try {
                        shopPromotionService.savePromotion(toPromotionDTO(row));
                        success++;
                    } catch (Exception e) {
                        failures.add("第 " + rowNo + " 行：" + e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("促销活动导入失败", e);
                return Result.error("导入失败：" + e.getMessage());
            }
        }
        if (failures.isEmpty()) {
            return Result.OK("文件导入成功！共 " + success + " 条。"
                    + "注意：多语言明细与关联商品不参与导入，请到编辑页补充。");
        }
        String detail = String.join("；", failures.subList(0, Math.min(failures.size(), 10)));
        return Result.OK("导入完成：成功 " + success + " 条，失败 " + failures.size() + " 条。失败明细：" + detail);
    }

    // ==================================================================
    // Excel 行 → DTO
    // ==================================================================

    /**
     * 导入行转 DTO：店铺按**编码**定位（运营手上只有 default 这种短码），
     * 并把名称补成一条 zh 翻译，其余明细留给编辑页。
     */
    private PromotionDTO toPromotionDTO(PromotionImportVO row) {
        String storeCode = StringUtils.trimToNull(row.getStoreCode());
        if (storeCode == null) {
            throw new JeecgBootException("店铺编码不能为空");
        }
        ShopStore store = shopStoreMapper.selectEnabledByCode(storeCode);
        if (store == null) {
            throw new JeecgBootException("店铺编码不存在或店铺已停用：" + storeCode);
        }
        String title = StringUtils.trimToNull(row.getTitle());
        if (title == null) {
            throw new JeecgBootException("活动名称不能为空");
        }

        PromotionDTO dto = new PromotionDTO();
        dto.setStoreId(store.getId());
        dto.setCode(StringUtils.trimToNull(row.getCode()));
        dto.setType(StringUtils.trimToNull(row.getType()));
        dto.setTitle(title);
        dto.setSubTitle(StringUtils.trimToNull(row.getSubTitle()));
        dto.setStartTime(parseImportDate(row.getStartTime(), "开始时间"));
        dto.setEndTime(parseImportDate(row.getEndTime(), "结束时间"));
        dto.setDiscountValue(parseDecimal(row.getDiscountValue(), "优惠力度"));
        dto.setThresholdAmount(parseDecimal(row.getThresholdAmount(), "满减门槛"));
        dto.setSeckillStock(parseInteger(row.getSeckillStock(), "秒杀限量"));
        dto.setLimitPerUser(parseInteger(row.getLimitPerUser(), "每人限购"));
        // 缺状态时按**停用**落库：导入的数据直接生效风险太大（时间窗/价格都没人复核过）
        dto.setStatus(StringUtils.defaultIfBlank(row.getStatus(), "N"));
        dto.setSortOrder(0);

        List<ShopPromotionTranslation> translations = new ArrayList<>(1);
        ShopPromotionTranslation zh = new ShopPromotionTranslation();
        zh.setLanguageCode("zh");
        zh.setTitle(title);
        zh.setSubTitle(StringUtils.trimToNull(row.getSubTitle()));
        translations.add(zh);
        dto.setTranslations(translations);
        // 关联商品刻意留空：Excel 单行无法表达"多商品 + 每商品活动价"
        dto.setProducts(new ArrayList<>());
        return dto;
    }

    private Date parseImportDate(String value, String field) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            throw new JeecgBootException(field + "不能为空");
        }
        SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN);
        format.setLenient(false);
        try {
            return format.parse(text);
        } catch (ParseException e) {
            throw new JeecgBootException(field + "格式不正确：" + text + "，应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private BigDecimal parseDecimal(String value, String field) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new JeecgBootException(field + "必须是数字：" + text);
        }
    }

    private Integer parseInteger(String value, String field) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            return null;
        }
        // Excel 里数字单元格可能带小数点（"100.0"），直接 parseInt 会抛 NumberFormatException
        try {
            return new BigDecimal(text).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new JeecgBootException(field + "必须是整数：" + text);
        }
    }

    /** 逗号分隔 id 串 → 去重列表 */
    private List<String> splitIds(String ids) {
        List<String> result = new ArrayList<>();
        if (StringUtils.isBlank(ids)) {
            return result;
        }
        for (String item : Arrays.asList(ids.split(","))) {
            String trimmed = StringUtils.trimToNull(item);
            if (trimmed != null && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
