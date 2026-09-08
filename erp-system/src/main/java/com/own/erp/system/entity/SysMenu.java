package com.own.erp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 菜单/权限(sys_menu,TODO#1 RBAC)。树形结构:parent_id=0 为根;
 *     menu_type 区分目录/菜单/按钮;perm_key 预留按钮级鉴权,当前接口按 role_key 鉴权(@PreAuthorize hasRole)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_menu")
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父菜单ID,0=根 */
    private Long parentId;

    /** 菜单名称 */
    private String menuName;

    /** 1目录 2菜单 3按钮 */
    private Integer menuType;

    /** 权限标识,如 system:user:list;按钮级必填,目录/菜单可空 */
    private String permKey;

    /** 前端路由路径 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 图标 */
    private String icon;

    /** 同级排序,小在前 */
    private Integer sort;

    /** 1显示 0隐藏 */
    private Integer visible;

    /** 1启用 0禁用 */
    private Integer status;

    /** created_at/updated_at 由数据库 DEFAULT CURRENT_TIMESTAMP / ON UPDATE 维护,实体不填 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除:0=正常,非0=已删(值=被删行id);delval=id 配合唯一键含 deleted,删后同键可重建(TODO#7) */
    @TableLogic(value = "0", delval = "id")
    private Long deleted;

    /** 子菜单(树形返回用,非表字段) */
    @TableField(exist = false)
    private List<SysMenu> children;
}
