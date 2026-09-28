package org.jeecg.modules.ck.sys.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.ck.sys.entity.SysLanguage;
import org.jeecg.modules.ck.sys.service.ISysLanguageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * 系统语言支持
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-07-20
 */

@Tag(name = "系统语言支持")
@RestController
@RequestMapping("/sys/lang")
@Slf4j
public class SysLanguageController extends JeecgController<SysLanguage, ISysLanguageService> {
    @Autowired
    private ISysLanguageService langService;

    /**
     * 分页列表查询
     *
     * @param sysLanguage
     * @param pageNo
     * @param pageSize
     * @param req
     * @return
     */
    //@AutoLog(value = "系统语言支持-分页列表查询")
    @Operation(summary = "系统语言支持-分页列表查询")
    @GetMapping(value = "/list")
    public Result<IPage<SysLanguage>> queryPageList(SysLanguage sysLanguage,
                                                    @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                    @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                    HttpServletRequest req) {


        QueryWrapper<SysLanguage> queryWrapper = QueryGenerator.initQueryWrapper(sysLanguage, req.getParameterMap());
        queryWrapper.orderByAsc("sort");
        Page<SysLanguage> page = new Page<SysLanguage>(pageNo, pageSize);
        IPage<SysLanguage> pageList = langService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    /**
     * 查询全部
     *
     * @return
     */
    //@AutoLog(value = "系统语言支持-查询全部")
    @Operation(summary = "系统语言支持-查询全部")
    @GetMapping
    public Result<List<SysLanguage>> queryList() {

        List<SysLanguage> list = langService.using();
        return Result.OK(list);
    }

    /**
     * 添加
     *
     * @param sysLanguage
     * @return
     */
    @AutoLog(value = "系统语言支持-添加")
    @Operation(summary = "系统语言支持-添加")
    @RequiresPermissions(value = {"ck:sys:lang:add", "ck:sys:lang:edit"}, logical = Logical.OR)
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody SysLanguage sysLanguage) {
        langService.save(sysLanguage);

        return Result.OK("添加成功！");
    }

    /**
     * 编辑
     *
     * @param sysLanguage
     * @return
     */
    @AutoLog(value = "系统语言支持-编辑")
    @Operation(summary = "系统语言支持-编辑")
    @RequiresPermissions("ck:sys:lang:edit")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<String> edit(@RequestBody SysLanguage sysLanguage) {
        langService.updateById(sysLanguage);
        return Result.OK("编辑成功!");
    }

    /**
     * 通过id删除
     *
     * @param id
     * @return
     */
    @AutoLog(value = "系统语言支持-通过id删除")
    @Operation(summary = "系统语言支持-通过id删除")
    @RequiresPermissions("ck:sys:lang:delete")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id", required = true) String id) {
        langService.removeById(id);
        return Result.OK("删除成功!");
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @AutoLog(value = "系统语言支持-批量删除")
    @Operation(summary = "系统语言支持-批量删除")
    @RequiresPermissions("ck:sys:lang:deleteBatch")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        this.langService.removeByIds(Arrays.asList(ids.split(",")));
        return Result.OK("批量删除成功!");
    }

    /**
     * 通过id查询
     *
     * @param id
     * @return
     */
    //@AutoLog(value = "系统语言支持-通过id查询")
    @Operation(summary = "系统语言支持-通过id查询")
    @GetMapping(value = "/queryById")
    public Result<SysLanguage> queryById(@RequestParam(name = "id", required = true) String id) {
        SysLanguage sysLanguage = langService.getById(id);
        if (sysLanguage == null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(sysLanguage);
    }

    /**
     * 导出excel
     *
     * @param request
     * @param sysLanguage
     */
    @RequiresPermissions("ck:sys:lang:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, SysLanguage sysLanguage) {
        return super.exportXls(request, sysLanguage, SysLanguage.class, "系统语言支持");
    }

    /**
     * 通过excel导入数据
     *
     * @param request
     * @param response
     * @return
     */
    @RequiresPermissions("ck:sys:lang:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, SysLanguage.class);
    }

}
