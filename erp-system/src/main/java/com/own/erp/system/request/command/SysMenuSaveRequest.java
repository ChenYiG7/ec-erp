package com.own.erp.system.request.command;

import com.own.erp.system.entity.SysMenu;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 菜单写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参);
 *     children 为服务端组装的树形出参,不入写侧
 */
@Builder
public record SysMenuSaveRequest(

        /** 父菜单ID,0=根 */
        @NotNull(message = "父菜单ID不能为空")
        Long parentId,

        /** 菜单名称 */
        @NotBlank(message = "菜单名称不能为空")
        String menuName,

        /** 1目录 2菜单 3按钮 */
        @NotNull(message = "菜单类型不能为空")
        Integer menuType,

        /** 权限标识,如 system:user:list;按钮级必填,目录/菜单可空 */
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
        Integer status
) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝) */
    public SysMenu toEntity() {
        SysMenu menu = new SysMenu();
        menu.setParentId(parentId);
        menu.setMenuName(menuName);
        menu.setMenuType(menuType);
        menu.setPermKey(permKey);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setIcon(icon);
        menu.setSort(sort);
        menu.setVisible(visible);
        menu.setStatus(status);
        return menu;
    }
}
