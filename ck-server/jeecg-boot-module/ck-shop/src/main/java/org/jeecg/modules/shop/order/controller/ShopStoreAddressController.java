package org.jeecg.modules.shop.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.shop.order.entity.dto.AddressDTO;
import org.jeecg.modules.shop.order.entity.vo.AddressVO;
import org.jeecg.modules.shop.order.service.IShopUserAddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 店铺端（前台）收货地址簿。
 * </p>
 *
 * <b>地址不按店铺隔离</b>：同一个人在不同店铺前台用的是同一份地址簿，
 * 所以这里不需要 {@code StoreResolver}——但仍然必须有登录态，
 * 所有操作都带 userId 过滤。
 *
 * <p><b>手机号不做脱敏</b>：这是买家自己的地址簿，号码是他自己填的，
 * 脱敏只会让他无法核对收货信息。后台侧的脱敏由后台接口负责。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Tag(name = "店铺端-收货地址")
@RestController
@RequestMapping("/shop/order/store/address")
@Slf4j
public class ShopStoreAddressController {

    @Autowired
    private IShopUserAddressService addressService;

    @Autowired
    private OrderRequestContextResolver contextResolver;

    @Operation(summary = "店铺端-我的地址列表")
    @GetMapping(value = "/list")
    public Result<List<AddressVO>> list(HttpServletRequest req) {
        LoginUser user = contextResolver.currentUser();
        return Result.OK(addressService.listMine(user.getId(), false));
    }

    @Operation(summary = "店铺端-地址详情")
    @GetMapping(value = "/detail")
    public Result<AddressVO> detail(String id, HttpServletRequest req) {
        LoginUser user = contextResolver.currentUser();
        return Result.OK(addressService.detail(user.getId(), id, false));
    }

    @Operation(summary = "店铺端-新增地址")
    @PostMapping(value = "/add")
    public Result<AddressVO> add(@RequestBody AddressDTO dto, HttpServletRequest req) {
        LoginUser user = contextResolver.currentUser();
        return Result.OK("地址已保存", addressService.create(user.getId(), user.getUsername(), dto));
    }

    @Operation(summary = "店铺端-编辑地址")
    @PostMapping(value = "/edit")
    public Result<AddressVO> edit(@RequestBody AddressDTO dto, HttpServletRequest req) {
        LoginUser user = contextResolver.currentUser();
        return Result.OK("地址已更新", addressService.update(user.getId(), user.getUsername(), dto));
    }

    @Operation(summary = "店铺端-删除地址(软删除)")
    @PostMapping(value = "/delete")
    public Result<String> delete(String id, HttpServletRequest req) {
        LoginUser user = contextResolver.currentUser();
        addressService.remove(user.getId(), id);
        return Result.OK("地址已删除");
    }

    @Operation(summary = "店铺端-设为默认地址")
    @PostMapping(value = "/set-default")
    public Result<String> setDefault(String id, HttpServletRequest req) {
        LoginUser user = contextResolver.currentUser();
        addressService.setDefault(user.getId(), id);
        return Result.OK("已设为默认地址");
    }
}
