package org.jeecg.modules.shop.common.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * <p>
 * 带数据可见域的 Excel 导出。
 * </p>
 *
 * <b>为什么不能用 {@code JeecgController.exportXls}：</b>
 * 框架那个实现只用 {@code QueryGenerator.initQueryWrapper} 拼条件，完全绕过
 * {@link org.jeecg.modules.shop.store.ShopOrgScopeResolver} —— 也就是说
 * **列表接口有可见域、导出接口没有**，一旦把 `shop-product:exportXls` 之类授权给运营，
 * 导出的文件里就是所有部门的商品。导出必须和列表用同一套可见域。
 *
 * 用法：{@code exportSupport.export(request, query, clazz, title, service, w -> applyOrgScope(w, scope))}
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */
@Component
public class ShopExportSupport {

    @Resource
    private JeecgBaseConfig jeecgBaseConfig;

    /**
     * @param query   查询实体（用于 QueryGenerator 自动拼条件）
     * @param service 该实体的 service（走 {@code list(wrapper)} 取数）
     * @param scope   可见域条件追加器；为 null 表示不限（总部/管理员）
     */
    public <T> ModelAndView export(HttpServletRequest request, T query, Class<T> clazz, String title,
                                   IService<T> service, Consumer<QueryWrapper<T>> scope) {
        QueryWrapper<T> wrapper = QueryGenerator.initQueryWrapper(query, request.getParameterMap());
        if (scope != null) {
            scope.accept(wrapper);
        }
        // 与框架保持一致：勾选导出时只导选中的行
        String selections = request.getParameter("selections");
        if (oConvertUtils.isNotEmpty(selections)) {
            wrapper.in("id", Arrays.asList(selections.split(",")));
        }
        List<T> exportList = service.list(wrapper);

        String exporter = "system";
        try {
            LoginUser sysUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
            if (sysUser != null && oConvertUtils.isNotEmpty(sysUser.getRealname())) {
                exporter = sysUser.getRealname();
            }
        } catch (Exception ignored) {
            // 公开接口/无 Shiro 上下文时忽略
        }

        ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
        mv.addObject(NormalExcelConstants.FILE_NAME, title);
        mv.addObject(NormalExcelConstants.CLASS, clazz);
        ExportParams exportParams = new ExportParams(title + "报表", "导出人:" + exporter, title, ExcelType.XSSF);
        exportParams.setImageBasePath(jeecgBaseConfig.getPath().getUpload());
        mv.addObject(NormalExcelConstants.PARAMS, exportParams);
        mv.addObject(NormalExcelConstants.DATA_LIST, exportList);

        String exportFields = request.getParameter(NormalExcelConstants.EXPORT_FIELDS);
        if (oConvertUtils.isNotEmpty(exportFields)) {
            mv.addObject(NormalExcelConstants.EXPORT_FIELDS, exportFields);
        }
        return mv;
    }
}
