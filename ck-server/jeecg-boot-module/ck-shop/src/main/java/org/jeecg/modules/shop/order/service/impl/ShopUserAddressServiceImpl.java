package org.jeecg.modules.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.order.entity.ShopUserAddress;
import org.jeecg.modules.shop.order.entity.dto.AddressDTO;
import org.jeecg.modules.shop.order.entity.vo.AddressVO;
import org.jeecg.modules.shop.order.mapper.ShopUserAddressMapper;
import org.jeecg.modules.shop.order.service.IShopUserAddressService;
import org.jeecg.modules.shop.order.util.ShopAddressTextUtils;
import org.jeecg.modules.shop.order.util.ShopMaskUtils;
import org.jeecg.modules.shop.region.entity.RegionResolution;
import org.jeecg.modules.shop.region.service.IShopRegionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户收货地址簿实现。
 * </p>
 *
 * <h3>三条纪律</h3>
 * <ol>
 *   <li><b>每个方法都带 userId 作为查询条件</b>，而不是"先按 id 查出来再判断归属"。
 *       后者只要有人漏写一次判断就是越权漏洞；前者漏写 userId 会直接查不到数据，
 *       是安全的失败方向。</li>
 *   <li><b>默认地址唯一</b>：设为默认时先把该用户其它地址的标记清掉。
 *       允许多个默认地址会让结算页"默认选中哪一个"变成随机行为。</li>
 *   <li><b>删除用软删</b>：订单快照里可能引用这个地址 ID，
 *       硬删会让"查看当时用的是哪个地址"永远无法回答。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Slf4j
@Service
public class ShopUserAddressServiceImpl
        extends ServiceImpl<ShopUserAddressMapper, ShopUserAddress>
        implements IShopUserAddressService {

    /** 地址簿上限：防止被脚本灌爆（既是业务约束也是资源保护） */
    private static final int MAX_ADDRESS_COUNT = 30;

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_PHONE_LENGTH = 30;
    private static final int MAX_DETAIL_LENGTH = 255;
    /** country_name 列宽：手填国名按列宽显式拒绝，不做静默截断 */
    private static final int MAX_COUNTRY_NAME_LENGTH = 100;

    @Autowired
    private IShopRegionService regionService;

    @Override
    public List<AddressVO> listMine(String userId, boolean masked, String locale) {
        requireUser(userId);
        List<ShopUserAddress> list = list(new LambdaQueryWrapper<ShopUserAddress>()
                .eq(ShopUserAddress::getUserId, userId)
                .eq(ShopUserAddress::getDelFlag, 0)
                .orderByDesc(ShopUserAddress::getIsDefault)
                .orderByDesc(ShopUserAddress::getLastUsedTime)
                .orderByDesc(ShopUserAddress::getCreateTime));
        // 展示名一次批量取好再逐条组装：逐条查译文是典型的 N+1（地址簿上限 30 条也会打出 90 次 SQL）
        AddressDisplay display = loadDisplay(list, locale);
        List<AddressVO> result = new ArrayList<>(list.size());
        for (ShopUserAddress address : list) {
            result.add(toVO(address, masked, display));
        }
        return result;
    }

    @Override
    public AddressVO detail(String userId, String addressId, boolean masked, String locale) {
        ShopUserAddress address = requireOwned(userId, addressId, false);
        return toVO(address, masked, loadDisplay(Collections.singletonList(address), locale));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO create(String userId, String username, AddressDTO dto, String locale) {
        requireUser(userId);
        validate(dto);
        // 三级级联校验必须在写库前做完：脏的 code 一旦落库，后面统计/对接物流都要返工
        RegionResolution region = regionService.resolveAddressRegion(
                dto.getCountryCode(), dto.getProvinceCode(), dto.getCityId());

        long count = count(new LambdaQueryWrapper<ShopUserAddress>()
                .eq(ShopUserAddress::getUserId, userId)
                .eq(ShopUserAddress::getDelFlag, 0));
        if (count >= MAX_ADDRESS_COUNT) {
            throw new JeecgBootException("收货地址最多保存 " + MAX_ADDRESS_COUNT + " 条，请先删除部分地址");
        }

        ShopUserAddress address = new ShopUserAddress()
                .setUserId(userId)
                .setUsername(username)
                .setReceiverName(StringUtils.trim(dto.getReceiverName()))
                .setReceiverPhone(StringUtils.trim(dto.getReceiverPhone()))
                .setProvince(provinceSnapshot(dto, region))
                .setCity(citySnapshot(dto, region))
                .setDistrict(StringUtils.trimToNull(dto.getDistrict()))
                .setDetailAddress(StringUtils.trim(dto.getDetailAddress()))
                .setPostalCode(StringUtils.trimToNull(dto.getPostalCode()))
                .setAddressTag(StringUtils.trimToNull(dto.getAddressTag()))
                .setUseCount(0)
                .setDelFlag(0)
                .setCreateBy(username)
                .setCreateTime(new Date())
                .setUpdateBy(username)
                .setUpdateTime(new Date());

        applyRegionCodes(address, region);
        address.setCountryName(countryNameSnapshot(dto, region));

        // 第一条地址自动成为默认地址：否则用户下第一单时没有默认项，结算页要手动选一次
        boolean firstOne = count == 0;
        boolean wantDefault = Boolean.TRUE.equals(dto.getIsDefault()) || firstOne;
        address.setIsDefault(wantDefault ? 1 : 0);
        save(address);

        if (wantDefault) {
            clearOtherDefault(userId, address.getId());
        }
        return toVO(address, false, loadDisplay(Collections.singletonList(address), locale));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO update(String userId, String username, AddressDTO dto, String locale) {
        requireUser(userId);
        if (dto == null || StringUtils.isBlank(dto.getId())) {
            throw new JeecgBootException("缺少地址标识");
        }
        validate(dto);
        RegionResolution region = regionService.resolveAddressRegion(
                dto.getCountryCode(), dto.getProvinceCode(), dto.getCityId());
        ShopUserAddress exists = requireOwned(userId, dto.getId(), true);

        ShopUserAddress patch = new ShopUserAddress()
                .setReceiverName(StringUtils.trim(dto.getReceiverName()))
                .setReceiverPhone(StringUtils.trim(dto.getReceiverPhone()))
                .setDistrict(StringUtils.trimToNull(dto.getDistrict()))
                .setDetailAddress(StringUtils.trim(dto.getDetailAddress()))
                .setPostalCode(StringUtils.trimToNull(dto.getPostalCode()))
                .setAddressTag(StringUtils.trimToNull(dto.getAddressTag()))
                .setUpdateBy(username)
                .setUpdateTime(new Date());

        // isDefault 为 null 表示"不改动"：编辑一个非默认地址时不应该把用户
        // 真正想保留的默认地址顺手取消掉
        if (dto.getIsDefault() != null) {
            patch.setIsDefault(Boolean.TRUE.equals(dto.getIsDefault()) ? 1 : 0);
        }

        boolean manualCountry = region == null && StringUtils.isNotBlank(dto.getCountryName());
        if (region == null && !manualCountry) {
            // 老入口（既没有国家码也没有手填国名）：一行不动，完全按旧逻辑走
            patch.setId(exists.getId())
                    .setProvince(StringUtils.trimToNull(dto.getProvince()))
                    .setCity(StringUtils.trimToNull(dto.getCity()));
            updateById(patch);
        } else {
            /*
             * 级联地址 与 "手填国名" 两种情况都走显式 set，理由有两个：
             * 1) 必须能写回 NULL —— 用户把"选城市"改回"手动填写"时 cityId 要清空；
             *    从泰国改成新加坡时，旧的省名/省码也不能留在新国家的地址上；
             *    从"选了国家"改回"手填国名"时，country_code 同样要清空（否则码与名对不上）。
             * 2) updateById 会跳过实体里的 null 字段（那是它的默认语义，别去改全局配置），
             *    表达不了"把它改成空"。
             * 注意这些列不能再同时出现在 patch 实体里：同一列在 SET 里出现两次，MySQL 直接报错。
             */
            update(patch, new LambdaUpdateWrapper<ShopUserAddress>()
                    .eq(ShopUserAddress::getId, exists.getId())
                    .set(ShopUserAddress::getCountryCode, region == null ? null : region.getCountryCode())
                    .set(ShopUserAddress::getCountryName, countryNameSnapshot(dto, region))
                    .set(ShopUserAddress::getProvinceCode, region == null ? null : region.getProvinceCode())
                    .set(ShopUserAddress::getProvince, provinceSnapshot(dto, region))
                    .set(ShopUserAddress::getCityId, region == null ? null : region.getCityId())
                    .set(ShopUserAddress::getCity, citySnapshot(dto, region)));
        }

        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearOtherDefault(userId, exists.getId());
        } else if (dto.getIsDefault() != null && !dto.getIsDefault()
                && exists.getIsDefault() != null && exists.getIsDefault() == 1) {
            // 把唯一的默认地址取消了：必须再补一个，否则该用户就没有默认地址了。
            // 不静默补，而是明确报错——用户的意思本来就是"我不想让它默认"，
            // 系统擅自把另一条设为默认会让人困惑。
            throw new JeecgBootException("至少需要保留一个默认地址，请先把其它地址设为默认");
        }

        ShopUserAddress latest = getById(exists.getId());
        return toVO(latest, false, loadDisplay(Collections.singletonList(latest), locale));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(String userId, String addressId) {
        requireUser(userId);
        ShopUserAddress exists = requireOwned(userId, addressId, true);
        boolean wasDefault = exists.getIsDefault() != null && exists.getIsDefault() == 1;

        // 软删除：订单快照引用了地址 ID，硬删会让"当时用的哪个地址"无法回答
        update(new LambdaUpdateWrapper<ShopUserAddress>()
                .eq(ShopUserAddress::getId, exists.getId())
                .eq(ShopUserAddress::getUserId, userId)
                .set(ShopUserAddress::getDelFlag, 1)
                .set(ShopUserAddress::getUpdateTime, new Date()));

        if (!wasDefault) {
            // 删的不是默认地址，默认项没受影响。
            // 此时"顺手"改任何 is_default 都是错的：用户只是想删掉一条旁支地址，
            // 系统不该借机动他当前选中的默认项。
            return;
        }

        // 删掉的正是默认地址：必须补一个，否则该用户变成"一条默认地址都没有"，
        // 结算页要用户每次手动选地址（见文件头"默认地址唯一"纪律）。
        // 这与 update() 里"不能取消唯一默认"是同一条不变量的两面：
        // 那里拒绝把用户显式取消的默认删空，这里则不允许删除动作留下空默认。
        ShopUserAddress candidate = getOne(new LambdaQueryWrapper<ShopUserAddress>()
                .eq(ShopUserAddress::getUserId, userId)
                .eq(ShopUserAddress::getDelFlag, 0)
                // 排序口径与 listMine 一致（去掉 is_default 这一层，待补的那条此时还不是默认）：
                // 先看最近用过的，再看最近建的。两者都相同/为空时也必须有确定顺序，
                // 否则同一份数据在两次删除中可能补出不同的默认地址，问题难以复现。
                .orderByDesc(ShopUserAddress::getLastUsedTime)
                .orderByDesc(ShopUserAddress::getCreateTime)
                .orderByDesc(ShopUserAddress::getId)
                .last("LIMIT 1"), false);

        if (candidate == null) {
            // 删掉的是最后一条地址：地址簿为空是合法状态，没有可提升的对象，
            // 静默返回即可——这里报错只会让用户"删不掉最后一条地址"。
            return;
        }

        // 上面已先软删，candidate 必然是"剩下的"地址；
        // 同一 @Transactional 内完成补位，中途失败会整体回滚，不会留下无默认的中间态。
        update(new LambdaUpdateWrapper<ShopUserAddress>()
                .eq(ShopUserAddress::getId, candidate.getId())
                .eq(ShopUserAddress::getUserId, userId)
                .eq(ShopUserAddress::getDelFlag, 0)
                .set(ShopUserAddress::getIsDefault, 1)
                .set(ShopUserAddress::getUpdateTime, new Date()));
        // 兜底清掉其它残留的默认标记。正常数据下这里影响 0 行，
        // 但历史数据可能因本次修复前的缺陷留下了多条 is_default=1，
        // 不清理的话结算页"默认选中哪一个"会重新变成随机行为。
        clearOtherDefault(userId, candidate.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(String userId, String addressId) {
        requireUser(userId);
        ShopUserAddress exists = requireOwned(userId, addressId, true);
        update(new LambdaUpdateWrapper<ShopUserAddress>()
                .eq(ShopUserAddress::getId, exists.getId())
                .set(ShopUserAddress::getIsDefault, 1)
                .set(ShopUserAddress::getUpdateTime, new Date()));
        clearOtherDefault(userId, exists.getId());
    }

    @Override
    public ShopUserAddress loadForOrder(String userId, String addressId) {
        if (StringUtils.isBlank(addressId)) {
            return null;
        }
        return requireOwned(userId, addressId, true);
    }

    @Override
    public RegionResolution resolveRegionForOrder(String countryCode, String provinceCode, Integer cityId) {
        // 直接复用写地址时的同一份校验：内联下单与地址簿绝不能是两套规则，
        // 否则同一份地址在"存进地址簿"和"直接下单"时能落到不同的 province 文字上
        return regionService.resolveAddressRegion(countryCode, provinceCode, cityId);
    }

    @Override
    public void markUsed(String userId, String addressId) {
        if (StringUtils.isBlank(addressId)) {
            return;
        }
        try {
            // 用 SQL 自增而不是读出来再写：两个并发的下单请求不会互相覆盖计数
            update(new LambdaUpdateWrapper<ShopUserAddress>()
                    .eq(ShopUserAddress::getId, addressId)
                    .eq(ShopUserAddress::getUserId, userId)
                    .setSql("use_count = use_count + 1")
                    .set(ShopUserAddress::getLastUsedTime, new Date()));
        } catch (Exception e) {
            // 统计用途，失败不影响下单
            log.warn("累加地址使用次数失败（不影响下单）：userId={}, addressId={}, err={}",
                    userId, addressId, e.getMessage());
        }
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private void clearOtherDefault(String userId, String keepId) {
        update(new LambdaUpdateWrapper<ShopUserAddress>()
                .eq(ShopUserAddress::getUserId, userId)
                .eq(ShopUserAddress::getDelFlag, 0)
                .ne(ShopUserAddress::getId, keepId)
                .set(ShopUserAddress::getIsDefault, 0));
    }

    private void requireUser(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new JeecgBootException("请先登录后再操作收货地址");
        }
    }

    /**
     * 按 (userId, addressId) 取地址，**查不到即视为无权访问**。
     *
     * <p>刻意不区分"地址不存在"和"地址是别人的"：返回不同的错误消息
     * 等于给攻击者一个探测"某个 ID 是否存在"的接口。
     */
    private ShopUserAddress requireOwned(String userId, String addressId, boolean mustAlive) {
        requireUser(userId);
        if (StringUtils.isBlank(addressId)) {
            throw new JeecgBootException("缺少地址标识");
        }
        LambdaQueryWrapper<ShopUserAddress> wrapper = new LambdaQueryWrapper<ShopUserAddress>()
                .eq(ShopUserAddress::getId, addressId)
                .eq(ShopUserAddress::getUserId, userId);
        if (mustAlive) {
            wrapper.eq(ShopUserAddress::getDelFlag, 0);
        }
        ShopUserAddress address = getOne(wrapper, false);
        if (address == null) {
            throw new JeecgBootException("收货地址不存在或无权访问");
        }
        return address;
    }

    private void validate(AddressDTO dto) {
        if (dto == null) {
            throw new JeecgBootException("请填写收货地址");
        }
        if (StringUtils.isBlank(dto.getReceiverName())) {
            throw new JeecgBootException("请填写收货人姓名");
        }
        if (dto.getReceiverName().trim().length() > MAX_NAME_LENGTH) {
            throw new JeecgBootException("收货人姓名过长");
        }
        if (StringUtils.isBlank(dto.getReceiverPhone())) {
            throw new JeecgBootException("请填写收货人手机号");
        }
        String phone = dto.getReceiverPhone().trim();
        if (phone.length() > MAX_PHONE_LENGTH) {
            throw new JeecgBootException("收货人手机号过长");
        }
        // 宽松校验：只要求"由数字和常见分隔符组成、位数在 6~20 之间"。
        // 严格的中国大陆手机号正则会把座机、境外号码、分机号全部拒掉，
        // 而拒掉一个真实用户远比放过一个格式奇怪的号码代价大（后者发货时人工能看出来）。
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 6 || digits.length() > 20) {
            throw new JeecgBootException("收货人手机号格式不正确");
        }
        if (StringUtils.isBlank(dto.getDetailAddress())) {
            throw new JeecgBootException("请填写详细地址");
        }
        if (dto.getDetailAddress().trim().length() > MAX_DETAIL_LENGTH) {
            throw new JeecgBootException("详细地址过长");
        }
        // 手填国名：按列宽显式拒绝（country_name 是 varchar(100)）。
        // 与姓名/详址同一套口径——超长就让用户改，不静默截断他的地址。
        if (dto.getCountryName() != null
                && dto.getCountryName().trim().length() > MAX_COUNTRY_NAME_LENGTH) {
            throw new JeecgBootException("国家/地区名称不能超过 " + MAX_COUNTRY_NAME_LENGTH + " 个字符");
        }
    }

    /**
     * 省/州文字：服务端解析到标准名就用它（落库必须是当地语言），
     * 解析不到（老入口、或用户手填省名）才采信前端文字。
     */
    private String provinceSnapshot(AddressDTO dto, RegionResolution region) {
        if (region != null && StringUtils.isNotBlank(region.getProvinceName())) {
            return region.getProvinceName();
        }
        return StringUtils.trimToNull(dto.getProvince());
    }

    /**
     * 城市文字：{@code cityId} 有值（服务端认得这个城市）才用标准名；
     * {@code cityId} 为空说明是手填（自贸区/工业园这类地址数据集里没有），
     * 此时前端文字是唯一来源，<b>绝不能覆盖</b>。
     */
    private String citySnapshot(AddressDTO dto, RegionResolution region) {
        if (region != null && StringUtils.isNotBlank(region.getCityName())) {
            return region.getCityName();
        }
        return StringUtils.trimToNull(dto.getCity());
    }

    /** 把级联权威码写到实体上（新增走这里；编辑走 update wrapper，因为要能写回 NULL） */
    private void applyRegionCodes(ShopUserAddress address, RegionResolution region) {
        if (region == null) {
            return;
        }
        address.setCountryCode(region.getCountryCode())
                .setProvinceCode(region.getProvinceCode())
                .setCityId(region.getCityId());
    }

    /**
     * 国名快照：{@code countryCode} 有值时<b>永远</b>用 region 解析出的当地语言国名，
     * 前端传的 {@code countryName} 一律忽略。
     *
     * <p>理由：码是权威、名是快照。若允许"码来自 region、名来自前端"，就会出现
     * {@code country_code='TH'} 而 {@code country_name='美国'} 的行——面单和统计各读一列，
     * 谁也说不清哪个对，而且这就是一个可以伪造的面单字段。
     *
     * <p>没有码但有手填国名时（自贸区/工业园那类兜底地址）才采信前端文字：
     * 这是这类地址唯一能拿到的国家名，不接住的话它在列表和面单上就没有国家。
     */
    private String countryNameSnapshot(AddressDTO dto, RegionResolution region) {
        if (region != null && StringUtils.isNotBlank(region.getCountryName())) {
            return region.getCountryName();
        }
        return StringUtils.trimToNull(dto.getCountryName());
    }

    private AddressVO toVO(ShopUserAddress address, boolean masked, AddressDisplay display) {
        if (address == null) {
            return null;
        }
        String phone = masked
                ? ShopMaskUtils.maskPhone(address.getReceiverPhone())
                : address.getReceiverPhone();
        AddressVO vo = new AddressVO()
                .setId(address.getId())
                .setReceiverName(address.getReceiverName())
                .setReceiverPhone(phone)
                .setProvince(address.getProvince())
                .setCity(address.getCity())
                .setDistrict(address.getDistrict())
                .setDetailAddress(address.getDetailAddress())
                .setPostalCode(address.getPostalCode())
                .setAddressTag(address.getAddressTag())
                .setCountryCode(address.getCountryCode())
                .setCountryName(address.getCountryName())
                .setProvinceCode(address.getProvinceCode())
                .setCityId(address.getCityId())
                .setIsDefault(address.getIsDefault() != null && address.getIsDefault() == 1)
                .setMasked(masked)
                .setUseCount(address.getUseCount())
                .setLastUsedTime(address.getLastUsedTime());
        // 面单文案分叉：老数据沿用原拼接，有国家信息的按国际面单格式。
        // 拼接规则只有一份实现（ShopAddressTextUtils），订单详情的 receiverFullAddress 也调它，
        // 两处各写一份必然漂移——"曼谷, 曼谷"只会出现在漏改的那一边，且没人分得清哪边对
        vo.setFullAddress(ShopAddressTextUtils.hasCountry(address.getCountryCode(), address.getCountryName())
                ? ShopAddressTextUtils.joinInternational(address.getDetailAddress(), address.getDistrict(),
                        address.getCity(), address.getProvince(), address.getPostalCode(), address.getCountryName())
                : ShopAddressTextUtils.joinLegacy(address.getProvince(), address.getCity(),
                        address.getDistrict(), address.getDetailAddress()));
        // 展示名与整串文案都按当前访客语言现算，落库值一个字都不动
        fillLocalized(vo, address, display);
        return vo;
    }

    /**
     * 补当前访客语言的展示名与整串地址。
     *
     * <h3>老数据必须与改造前完全一致</h3>
     * 既没有 {@code countryCode} 也没有 {@code countryName} = 改造前存的地址（或来自未改造入口）。
     * 那种地址没有权威码可反查译文，展示名一律给 null，而
     * {@code fullAddressLocalized} 直接等于 {@code fullAddress}——
     * 前端把新字段接上去以后，老地址看起来必须跟以前一模一样。
     */
    private void fillLocalized(AddressVO vo, ShopUserAddress address, AddressDisplay display) {
        if (!ShopAddressTextUtils.hasCountry(address.getCountryCode(), address.getCountryName())) {
            vo.setFullAddressLocalized(vo.getFullAddress());
            return;
        }
        String countryCode = address.getCountryCode();
        // 回退链：译文/拉丁名（批量查询已算好）→ 落库的当地语言快照 → 什么都不给。
        // 手填国名没有码可查，display 里取不到，自然退回落库的手填国名。
        vo.setCountryDisplay(firstNonBlank(display.country(countryCode), address.getCountryName()));
        vo.setProvinceDisplay(firstNonBlank(display.state(address.getProvinceCode()), address.getProvince()));
        vo.setCityDisplay(firstNonBlank(display.city(address.getCityId()), address.getCity()));
        vo.setFullAddressLocalized(ShopAddressTextUtils.joinInternational(address.getDetailAddress(),
                address.getDistrict(), vo.getCityDisplay(), vo.getProvinceDisplay(),
                address.getPostalCode(), vo.getCountryDisplay()));
    }

    /**
     * 一次把整批地址需要的展示名查回来（3 条 SQL，与地址条数无关）。
     *
     * <p>逐条地址各查一次国家/州/城市会打出 3N 条 SQL，30 条地址就是 90 次往返——
     * 这正是"地址列表看着简单、翻页却慢"的常见来源。
     */
    private AddressDisplay loadDisplay(List<ShopUserAddress> addresses, String locale) {
        AddressDisplay display = new AddressDisplay();
        if (addresses == null || addresses.isEmpty() || StringUtils.isBlank(locale)) {
            return display;
        }
        List<String> countryCodes = new ArrayList<>();
        List<String> provinceCodes = new ArrayList<>();
        List<Integer> cityIds = new ArrayList<>();
        for (ShopUserAddress address : addresses) {
            if (address == null || StringUtils.isBlank(address.getCountryCode())) {
                // 老数据没有权威码，反查不出什么，跳过（不让它污染 IN 列表）
                continue;
            }
            countryCodes.add(address.getCountryCode());
            if (StringUtils.isNotBlank(address.getProvinceCode())) {
                provinceCodes.add(address.getProvinceCode());
            }
            if (address.getCityId() != null) {
                cityIds.add(address.getCityId());
            }
        }
        if (countryCodes.isEmpty()) {
            return display;
        }
        display.countries.putAll(regionService.countryDisplayNames(countryCodes, locale));
        display.states.putAll(regionService.stateDisplayNames(provinceCodes, locale));
        display.cities.putAll(regionService.cityDisplayNames(cityIds, locale));
        return display;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : StringUtils.trimToNull(second);
    }

    /** 一批地址的展示名缓存（key 分别是 ISO alpha-2 / province_code / cities.id） */
    private static final class AddressDisplay {
        private final Map<String, String> countries = new HashMap<>();
        private final Map<String, String> states = new HashMap<>();
        private final Map<Integer, String> cities = new HashMap<>();

        String country(String code) {
            return code == null ? null : countries.get(code);
        }

        String state(String code) {
            return code == null ? null : states.get(code);
        }

        String city(Integer id) {
            return id == null ? null : cities.get(id);
        }
    }
}
