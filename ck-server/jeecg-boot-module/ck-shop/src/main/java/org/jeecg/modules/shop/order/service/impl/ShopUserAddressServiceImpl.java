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
import org.jeecg.modules.shop.order.util.ShopMaskUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

    @Override
    public List<AddressVO> listMine(String userId, boolean masked) {
        requireUser(userId);
        List<ShopUserAddress> list = list(new LambdaQueryWrapper<ShopUserAddress>()
                .eq(ShopUserAddress::getUserId, userId)
                .eq(ShopUserAddress::getDelFlag, 0)
                .orderByDesc(ShopUserAddress::getIsDefault)
                .orderByDesc(ShopUserAddress::getLastUsedTime)
                .orderByDesc(ShopUserAddress::getCreateTime));
        List<AddressVO> result = new ArrayList<>(list.size());
        for (ShopUserAddress address : list) {
            result.add(toVO(address, masked));
        }
        return result;
    }

    @Override
    public AddressVO detail(String userId, String addressId, boolean masked) {
        return toVO(requireOwned(userId, addressId, false), masked);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO create(String userId, String username, AddressDTO dto) {
        requireUser(userId);
        validate(dto);

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
                .setProvince(StringUtils.trimToNull(dto.getProvince()))
                .setCity(StringUtils.trimToNull(dto.getCity()))
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

        // 第一条地址自动成为默认地址：否则用户下第一单时没有默认项，结算页要手动选一次
        boolean firstOne = count == 0;
        boolean wantDefault = Boolean.TRUE.equals(dto.getIsDefault()) || firstOne;
        address.setIsDefault(wantDefault ? 1 : 0);
        save(address);

        if (wantDefault) {
            clearOtherDefault(userId, address.getId());
        }
        return toVO(address, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO update(String userId, String username, AddressDTO dto) {
        requireUser(userId);
        if (dto == null || StringUtils.isBlank(dto.getId())) {
            throw new JeecgBootException("缺少地址标识");
        }
        validate(dto);
        ShopUserAddress exists = requireOwned(userId, dto.getId(), true);

        ShopUserAddress patch = new ShopUserAddress()
                .setId(exists.getId())
                .setReceiverName(StringUtils.trim(dto.getReceiverName()))
                .setReceiverPhone(StringUtils.trim(dto.getReceiverPhone()))
                .setProvince(StringUtils.trimToNull(dto.getProvince()))
                .setCity(StringUtils.trimToNull(dto.getCity()))
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
        updateById(patch);

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
        return toVO(latest, false);
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
    }

    private AddressVO toVO(ShopUserAddress address, boolean masked) {
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
                .setIsDefault(address.getIsDefault() != null && address.getIsDefault() == 1)
                .setMasked(masked)
                .setUseCount(address.getUseCount())
                .setLastUsedTime(address.getLastUsedTime());
        vo.setFullAddress(joinAddress(address));
        return vo;
    }

    /** 拼完整地址：省市区可能缺失（境外地址），缺失的段直接跳过，不留出多余空格 */
    private String joinAddress(ShopUserAddress address) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, address.getProvince());
        appendPart(sb, address.getCity());
        appendPart(sb, address.getDistrict());
        appendPart(sb, address.getDetailAddress());
        return sb.toString();
    }

    private void appendPart(StringBuilder sb, String part) {
        if (StringUtils.isNotBlank(part)) {
            sb.append(part.trim());
        }
    }
}
