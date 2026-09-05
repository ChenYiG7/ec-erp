package com.own.erp.system.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.entity.SysMenu;
import com.own.erp.system.entity.SysRole;
import com.own.erp.system.mapper.SysMenuMapper;
import com.own.erp.system.mapper.SysRoleMapper;
import com.own.erp.system.mapper.SysRoleMenuMapper;
import com.own.erp.system.mapper.SysUserRoleMapper;
import com.own.erp.system.request.command.SysMenuSaveRequest;
import com.own.erp.system.response.SysMenuResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 菜单/权限服务(TODO#1 RBAC):树组装、用户维度角色与权限查询、角色-菜单与用户-角色绑定、菜单 CRUD。
 *     API 模型收口(docs/07 §1):写入参 command/SysMenuSaveRequest,出参 response/SysMenuResponse(树形递归),entity 不出本层
 */
@Service
@RequiredArgsConstructor
public class SysMenuService {

    private static final long ROOT_PARENT_ID = 0L;
    private static final int STATUS_ENABLED = 1;

    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    /** 全量菜单树(管理端用,含禁用节点) */
    public List<SysMenuResponse> tree() {
        List<SysMenu> all = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSort).orderByAsc(SysMenu::getId));
        return SysMenuResponse.fromTree(buildTree(all));
    }

    /** 用户可见菜单树(仅启用节点,前端侧边栏用);未绑角色/菜单返回空列表 */
    public List<SysMenuResponse> treeByUserId(Long userId) {
        List<Long> menuIds = menuIdsOfUser(userId);
        if (CollUtil.isEmpty(menuIds)) {
            return List.of();
        }
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, menuIds)
                .eq(SysMenu::getStatus, STATUS_ENABLED)
                .orderByAsc(SysMenu::getSort).orderByAsc(SysMenu::getId));
        return SysMenuResponse.fromTree(buildTree(menus));
    }

    /** 用户角色标识列表(仅启用角色),登录签发 JWT 用 */
    public List<String> listRoleKeysByUserId(Long userId) {
        List<Long> roleIds = listRoleIdsByUserId(userId);
        if (CollUtil.isEmpty(roleIds)) {
            return List.of();
        }
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds).eq(SysRole::getStatus, STATUS_ENABLED));
        return roles.stream().map(SysRole::getRoleKey).toList();
    }

    /** 用户已绑定的角色ID(管理端回显) */
    public List<Long> listRoleIdsByUserId(Long userId) {
        return userRoleMapper.selectRoleIdsByUserId(userId);
    }

    /** 用户权限标识去重集合(perm_key),预留按钮级鉴权 */
    public List<String> listPermKeysByUserId(Long userId) {
        List<Long> menuIds = menuIdsOfUser(userId);
        if (CollUtil.isEmpty(menuIds)) {
            return List.of();
        }
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, menuIds).eq(SysMenu::getStatus, STATUS_ENABLED));
        return menus.stream()
                .map(SysMenu::getPermKey)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }

    /** 用户-角色全量重绑:先删后插,同事务 */
    @Transactional(rollbackFor = Exception.class)
    public void assignRolesToUser(Long userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        for (Long roleId : roleIds) {
            userRoleMapper.insert(userId, roleId);
        }
    }

    /** 角色-菜单全量重绑:先删后插,同事务 */
    @Transactional(rollbackFor = Exception.class)
    public void assignMenusToRole(Long roleId, List<Long> menuIds) {
        roleMenuMapper.deleteByRoleId(roleId);
        for (Long menuId : menuIds) {
            roleMenuMapper.insert(roleId, menuId);
        }
    }

    /** 角色已绑定的菜单ID(管理端回显) */
    public List<Long> roleMenuIds(Long roleId) {
        return roleMenuMapper.selectMenuIdsByRoleId(roleId);
    }

    public Long create(SysMenuSaveRequest request) {
        SysMenu menu = request.toEntity();
        menuMapper.insert(menu);
        return menu.getId();
    }

    /** 更新菜单:禁止把父级设成自身(防止单级成环;多级成环由人工管理端规避,树接口按"父不在集合内即根"容错) */
    public void update(Long id, SysMenuSaveRequest request) {
        if (id.equals(request.parentId())) {
            throw new BusinessException("父菜单不能是自身");
        }
        SysMenu menu = request.toEntity();
        menu.setId(id);
        menuMapper.updateById(menu);
    }

    /** 删除菜单:有子菜单则拒绝;同事务清理角色-菜单引用 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long childCount = menuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("存在子菜单,请先删除子菜单");
        }
        menuMapper.deleteById(id);
        roleMenuMapper.deleteByMenuId(id);
    }

    /** 用户全部角色能看到的菜单ID并集(不去重不影响后续 in 查询) */
    private List<Long> menuIdsOfUser(Long userId) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        if (CollUtil.isEmpty(roleIds)) {
            return List.of();
        }
        return roleMenuMapper.selectMenuIdsByRoleIds(roleIds);
    }

    /**
     * 内存引用组装森林:父节点在集合内的挂到父下,父不在集合内的(含 parent_id=0)按根返回。
     * 入参须已按 sort/id 排序,子节点顺序随父列表稳定
     */
    private List<SysMenu> buildTree(List<SysMenu> menus) {
        Map<Long, SysMenu> byId = new LinkedHashMap<>(menus.size() * 4 / 3 + 1);
        for (SysMenu menu : menus) {
            byId.put(menu.getId(), menu);
        }
        List<SysMenu> roots = new ArrayList<>();
        for (SysMenu menu : menus) {
            SysMenu parent = byId.get(menu.getParentId());
            if (parent == null) {
                roots.add(menu);
                continue;
            }
            if (parent.getChildren() == null) {
                parent.setChildren(new ArrayList<>());
            }
            parent.getChildren().add(menu);
        }
        return roots;
    }
}
