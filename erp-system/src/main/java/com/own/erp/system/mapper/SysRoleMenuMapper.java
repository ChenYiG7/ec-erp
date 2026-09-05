package com.own.erp.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 角色-菜单关联(sys_role_menu,联合主键,TODO#1 RBAC)。
 *     关联表不走 MP BaseMapper(联合主键非单实体),直接注解 SQL;
 *     绑定变更统一"先删后插"全量重绑,幂等且无中间态
 */
public interface SysRoleMenuMapper {

    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    /** 多角色的并集菜单ID(查用户权限用,in 元素由角色数决定,量级极小) */
    @Select("<script>SELECT menu_id FROM sys_role_menu WHERE role_id IN "
            + "<foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Long> selectMenuIdsByRoleIds(@Param("roleIds") List<Long> roleIds);

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /** 删菜单时清理引用 */
    @Delete("DELETE FROM sys_role_menu WHERE menu_id = #{menuId}")
    int deleteByMenuId(@Param("menuId") Long menuId);

    @Insert("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (#{roleId}, #{menuId})")
    int insert(@Param("roleId") Long roleId, @Param("menuId") Long menuId);
}
