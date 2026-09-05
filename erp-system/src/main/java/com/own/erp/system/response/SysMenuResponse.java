package com.own.erp.system.response;

import com.own.erp.system.entity.SysMenu;
import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 菜单对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段),树形递归——
 *     子节点集合由 Service 组装后经 fromTree 递归转换,entity 的 children 不外泄
 */
@Builder
public record SysMenuResponse(

        /** 主键 */
        Long id,

        /** 父菜单ID,0=根 */
        Long parentId,

        /** 菜单名称 */
        String menuName,

        /** 1目录 2菜单 3按钮 */
        Integer menuType,

        /** 权限标识,如 system:user:list */
        String permKey,

        /** 前端路由路径 */
        String path,

        /** 前端组件路径 */
        String component,

        /** 图标 */
        String icon,

        /** 同级排序,小在前 */
        Integer sort,

        /** 1显示 0隐藏 */
        Integer visible,

        /** 1启用 0禁用 */
        Integer status,

        /** 子菜单(entity 组树后递归转换,叶子为 null) */
        List<SysMenuResponse> children
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static SysMenuResponse from(SysMenu menu) {
        return SysMenuResponse.builder()
                .id(menu.getId())
                .parentId(menu.getParentId())
                .menuName(menu.getMenuName())
                .menuType(menu.getMenuType())
                .permKey(menu.getPermKey())
                .path(menu.getPath())
                .component(menu.getComponent())
                .icon(menu.getIcon())
                .sort(menu.getSort())
                .visible(menu.getVisible())
                .status(menu.getStatus())
                // 条件装配收进 builder 链(record 不可变,构造后无法补字段);叶子菜单 children 为 null
                .children(menu.getChildren() == null ? null : fromTree(menu.getChildren()))
                .build();
    }

    /** 实体树 → Response 树(保持 Service 组好的顺序与层级) */
    public static List<SysMenuResponse> fromTree(List<SysMenu> roots) {
        if (roots == null) {
            return List.of();
        }
        return roots.stream().map(SysMenuResponse::from).toList();
    }
}
