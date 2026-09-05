package com.own.erp.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 用户-角色关联(sys_user_role,联合主键,TODO#1 RBAC)。
 *     关联表不走 MP BaseMapper(联合主键非单实体),直接注解 SQL;
 *     绑定变更统一"先删后插"全量重绑,幂等且无中间态
 */
public interface SysUserRoleMapper {

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /** 绑定该角色的用户数(删角色前校验用) */
    @Select("SELECT COUNT(*) FROM sys_user_role WHERE role_id = #{roleId}")
    Long countByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insert(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
