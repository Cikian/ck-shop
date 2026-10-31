package org.jeecg.modules.shop.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.order.entity.ShopUserAddress;
import org.jeecg.modules.shop.order.entity.dto.AddressDTO;
import org.jeecg.modules.shop.order.entity.vo.AddressVO;
import org.jeecg.modules.shop.region.entity.RegionResolution;

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
 * <h3>为什么查询类方法要带 {@code locale}</h3>
 * 地址里存的是<b>当地语言文字</b>（面单要用的写法），而界面上要显示访客语言的译名。
 * 译名是"拿编码反查译文现算"的：{@code countryCode/provinceCode/cityId} 反查
 * {@code translations} 得到中文"曼谷"或法文"Bangkok"。
 * 这个现算必须知道"当前是哪个访客在问"，而服务层不该去猜请求上下文
 * （那样单测里根本跑不起来，也没法给后台脚本复用），所以由 Controller 显式传进来。
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
     * @param locale 访客 locale（zh/en/fr/zh-TW…），用于现算展示名；可传 null 表示"不翻译"
     */
    List<AddressVO> listMine(String userId, boolean masked, String locale);

    /** 地址详情（含归属校验） */
    AddressVO detail(String userId, String addressId, boolean masked, String locale);

    /** 新增地址 */
    AddressVO create(String userId, String username, AddressDTO dto, String locale);

    /** 编辑地址（含归属校验） */
    AddressVO update(String userId, String username, AddressDTO dto, String locale);

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

    /**
     * 按"地址簿同一套规则"解析三级级联码（下单内联地址复用）。
     *
     * <p>订单里不能只写 {@code receiverCity} 文字：物流对接要的是 {@code cities.id}。
     * 内联地址如果不走校验，就会出现"地址簿里有权威码、订单里没有"的割裂，
     * 后面按国家统计运费、对接物流都得返工。所以这里把地址簿的解析结果直接交给订单用。
     *
     * @return {@code countryCode} 为空时返回 null，调用方按旧逻辑处理
     */
    RegionResolution resolveRegionForOrder(String countryCode, String provinceCode, Integer cityId);

    /** 下单成功后累加地址使用次数与最近使用时间（用于智能排序） */
    void markUsed(String userId, String addressId);
}
