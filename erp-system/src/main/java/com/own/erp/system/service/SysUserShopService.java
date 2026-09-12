package com.own.erp.system.service;

import com.own.erp.system.mapper.SysUserShopMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 用户-店铺数据授权服务(#27① 数据权限方案A:店铺轴,docs/plans/27-rbac-enhance.md §2.3):
 *     sys_user_shop 授权集的读写收口本服务;消费方 = CurrentUserApiImpl(erp-api,
 *     currentShopIds 数据源)与用户管理端点(GET/PUT /{id}/shops)。
 *     角色语义不在此判定——admin 不限由 CurrentUserApiImpl 按角色短路,本表/本服务只管授权集本身;
 *     店铺存在性校验不做:授权指向已删店铺 = 过滤条件永远落空,无数据完整性风险(与业务引用计数不同)
 */
@Service
@RequiredArgsConstructor
public class SysUserShopService {

    private final SysUserShopMapper userShopMapper;

    /** 用户授权店铺ID集(原样返回,不判角色;可空 = 该用户未授权任何店铺) */
    public List<Long> listShopIdsByUserId(Long userId) {
        return userShopMapper.selectShopIdsByUserId(userId);
    }

    /** 用户-店铺授权全量重绑:先删后插,同事务;入参去重(联合主键防重) */
    @Transactional(rollbackFor = Exception.class)
    public void assignShopsToUser(Long userId, List<Long> shopIds) {
        userShopMapper.deleteByUserId(userId);
        if (shopIds == null) {
            return;
        }
        shopIds.stream().distinct().forEach(shopId -> userShopMapper.insert(userId, shopId));
    }
}
