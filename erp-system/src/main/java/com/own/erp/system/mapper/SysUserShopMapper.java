package com.own.erp.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 用户-店铺数据授权关联(sys_user_shop,联合主键,#27① 数据权限方案A:店铺轴)。
 *     关联表不走 MP BaseMapper(联合主键非单实体),直接注解 SQL(同 SysUserRoleMapper 先例);
 *     授权变更统一"先删后插"全量重绑,幂等且无中间态。
 *     admin 用户的"不限"语义不落本表(无行),由 CurrentUserApiImpl 按角色短路——
 *     本表只承载非 admin 用户的授权集,空集 = 不可见任何店铺数据
 */
public interface SysUserShopMapper {

    @Select("SELECT shop_id FROM sys_user_shop WHERE user_id = #{userId}")
    List<Long> selectShopIdsByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_user_shop WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_shop (user_id, shop_id) VALUES (#{userId}, #{shopId})")
    int insert(@Param("userId") Long userId, @Param("shopId") Long shopId);
}
