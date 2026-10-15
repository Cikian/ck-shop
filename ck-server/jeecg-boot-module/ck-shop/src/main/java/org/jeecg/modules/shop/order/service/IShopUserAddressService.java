package org.jeecg.modules.shop.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.order.entity.ShopUserAddress;
import org.jeecg.modules.shop.order.entity.dto.AddressDTO;
import org.jeecg.modules.shop.order.entity.vo.AddressVO;

import java.util.List;

/**
 * <p>
 * 用户收货地址簿 Service。
 * </p>
 *
 * <b>所有方法都必须带 userId</b>：地址是隐私数据，
 * 按 id 操作的接口如果只校验"存在"而不校验"归属"，
 * 换个 id 就能读别人的地址——这是最典型的越权。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
public interface IShopUserAddressService extends IService<ShopUserAddress> {

    /**
     * 我的地址列表（默认地址排最前，其余按最近使用时间倒序）。
     *
     * @param masked 是否脱敏手机号。买家看自己的地址簿传 false
     *               （他自己填的号码，脱敏只会让他无法核对）；
     *               任何后台/日志场景传 true。
     */
    List<AddressVO> listMine(String userId, boolean masked);

    /** 地址详情（含归属校验） */
    AddressVO detail(String userId, String addressId, boolean masked);

    /** 新增地址 */
    AddressVO create(String userId, String username, AddressDTO dto);

    /** 编辑地址（含归属校验） */
    AddressVO update(String userId, String username, AddressDTO dto);

    /**
     * 删除地址（软删除；归属校验）。
     *
     * <p>删除的若正好是当前默认地址，会在同一事务里把剩下的某条（最近使用优先、
     * 其次最近创建）重新设为默认，保证"只要地址簿非空就一定有默认地址"；
     * 删除非默认地址不做任何额外改动；删掉最后一条时地址簿为空，
     * 此时 0 条默认是合法状态。
     */
    void remove(String userId, String addressId);

    /** 设为默认地址（会清掉该用户其它地址的默认标记，保证有且只有一个默认） */
    void setDefault(String userId, String addressId);

    /**
     * 取订单要用的地址快照。
     *
     * @param addressId 地址簿ID；为空时表示"直接传地址"，此时返回 null 由调用方用内联字段
     * @return 地址实体（调用方负责转成订单上的快照字段）
     */
    ShopUserAddress loadForOrder(String userId, String addressId);

    /** 下单成功后累加地址使用次数与最近使用时间（用于智能排序） */
    void markUsed(String userId, String addressId);
}
