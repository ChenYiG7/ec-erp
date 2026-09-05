package com.own.erp.system.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.entity.SysMenu;
import com.own.erp.system.mapper.SysMenuMapper;
import com.own.erp.system.mapper.SysRoleMapper;
import com.own.erp.system.mapper.SysRoleMenuMapper;
import com.own.erp.system.mapper.SysUserRoleMapper;
import com.own.erp.system.request.command.SysMenuSaveRequest;
import com.own.erp.system.response.SysMenuResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/5
 * @Description : SysMenuService 单测(AIR:mock Mapper,docs/07 §10)。
 *     重点:内存组树(父不在集合内按根容错)/绑定先删后插/更新禁自环/删除子菜单拦截。
 *     边界:已绑定角色的 in 查询分支(treeByUserId 过滤禁用、listRoleKeys 启用过滤、listPermKeys 去重)
 *     受 MP .in() 急切解析列元数据坑限制纯 Mockito 不可直测(#5/#11 两度验证先例,逐单 eq 同款规避),
 *     仅覆盖 CollUtil 空集合短路分支
 */
class SysMenuServiceTest {

    private SysMenuMapper menuMapper;
    private SysRoleMapper roleMapper;
    private SysUserRoleMapper userRoleMapper;
    private SysRoleMenuMapper roleMenuMapper;
    private SysMenuService service;

    @BeforeEach
    void setUp() {
        menuMapper = mock(SysMenuMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        roleMenuMapper = mock(SysRoleMenuMapper.class);
        service = new SysMenuService(menuMapper, roleMapper, userRoleMapper, roleMenuMapper);
    }

    private SysMenu menu(long id, long parentId, String name) {
        return SysMenu.builder().id(id).parentId(parentId).menuName(name).menuType(1).status(1).build();
    }

    @Test
    void treeNestsChildrenAndTreatsMissingParentAsRoot() {
        // selectList 已按 sort/id 排序(buildTree 前置约定):根1 → 子2,孤儿3(父99不在集合内)
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(1, 0, "系统管理"), menu(2, 1, "用户管理"), menu(3, 99, "孤儿节点")));

        List<SysMenuResponse> tree = service.tree();

        assertEquals(2, tree.size());
        assertEquals(1L, tree.get(0).id());
        assertEquals(1, tree.get(0).children().size());
        assertEquals(2L, tree.get(0).children().get(0).id());
        assertEquals("用户管理", tree.get(0).children().get(0).menuName());
        // 孤儿(脏数据/父被删)按根返回,树接口容错不丢节点
        assertEquals(3L, tree.get(1).id());
        assertTrue(tree.get(1).children() == null);
    }

    @Test
    void treeByUserIdReturnsEmptyWhenUserHasNoRoles() {
        when(userRoleMapper.selectRoleIdsByUserId(7L)).thenReturn(List.of());

        assertEquals(List.of(), service.treeByUserId(7L));
        verify(menuMapper, never()).selectList(any());
    }

    @Test
    void treeByUserIdReturnsEmptyWhenRolesHaveNoMenus() {
        when(userRoleMapper.selectRoleIdsByUserId(7L)).thenReturn(List.of(5L));
        when(roleMenuMapper.selectMenuIdsByRoleIds(List.of(5L))).thenReturn(List.of());

        assertEquals(List.of(), service.treeByUserId(7L));
        verify(menuMapper, never()).selectList(any());
    }

    @Test
    void listRoleKeysByUserIdReturnsEmptyWhenNoRoles() {
        when(userRoleMapper.selectRoleIdsByUserId(7L)).thenReturn(List.of());

        assertEquals(List.of(), service.listRoleKeysByUserId(7L));
        verify(roleMapper, never()).selectList(any());
    }

    @Test
    void listPermKeysByUserIdReturnsEmptyWhenNoRoles() {
        when(userRoleMapper.selectRoleIdsByUserId(7L)).thenReturn(List.of());

        assertEquals(List.of(), service.listPermKeysByUserId(7L));
        verify(menuMapper, never()).selectList(any());
    }

    @Test
    void listRoleIdsByUserIdReturnsMapperResult() {
        when(userRoleMapper.selectRoleIdsByUserId(7L)).thenReturn(List.of(5L, 6L));
        assertEquals(List.of(5L, 6L), service.listRoleIdsByUserId(7L));
    }

    @Test
    void roleMenuIdsReturnsMapperResult() {
        when(roleMenuMapper.selectMenuIdsByRoleId(3L)).thenReturn(List.of(1L, 2L));
        assertEquals(List.of(1L, 2L), service.roleMenuIds(3L));
    }

    @Test
    void assignRolesToUserRebindsAllInOrder() {
        service.assignRolesToUser(7L, List.of(5L, 6L));

        InOrder inOrder = inOrder(userRoleMapper);
        inOrder.verify(userRoleMapper).deleteByUserId(7L);
        inOrder.verify(userRoleMapper).insert(7L, 5L);
        inOrder.verify(userRoleMapper).insert(7L, 6L);
    }

    @Test
    void assignMenusToRoleRebindsAllInOrder() {
        service.assignMenusToRole(3L, List.of(1L, 2L));

        InOrder inOrder = inOrder(roleMenuMapper);
        inOrder.verify(roleMenuMapper).deleteByRoleId(3L);
        inOrder.verify(roleMenuMapper).insert(3L, 1L);
        inOrder.verify(roleMenuMapper).insert(3L, 2L);
    }

    @Test
    void createInsertsMappedEntity() {
        SysMenuSaveRequest request = SysMenuSaveRequest.builder().parentId(0L).menuName("报表")
                .menuType(1).permKey("report:view").path("/report").sort(9).visible(1).status(1).build();

        service.create(request);

        ArgumentCaptor<SysMenu> captor = ArgumentCaptor.forClass(SysMenu.class);
        verify(menuMapper).insert(captor.capture());
        SysMenu row = captor.getValue();
        assertEquals(0L, row.getParentId());
        assertEquals("报表", row.getMenuName());
        assertEquals("report:view", row.getPermKey());
        assertEquals(9, row.getSort());
    }

    @Test
    void updateRejectsSelfParent() {
        SysMenuSaveRequest request = SysMenuSaveRequest.builder().parentId(2L).menuName("用户管理")
                .menuType(2).build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(2L, request));

        assertEquals("父菜单不能是自身", ex.getMessage());
        verify(menuMapper, never()).updateById(any(SysMenu.class));
    }

    @Test
    void updateMapsEntityAndSetsId() {
        SysMenuSaveRequest request = SysMenuSaveRequest.builder().parentId(0L).menuName("用户管理")
                .menuType(2).path("/user").status(1).build();

        service.update(2L, request);

        ArgumentCaptor<SysMenu> captor = ArgumentCaptor.forClass(SysMenu.class);
        verify(menuMapper).updateById(captor.capture());
        assertEquals(2L, captor.getValue().getId());
        assertEquals(0L, captor.getValue().getParentId());
    }

    @Test
    void deleteRejectsWhenChildrenExist() {
        when(menuMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));

        assertEquals("存在子菜单,请先删除子菜单", ex.getMessage());
        verify(menuMapper, never()).deleteById(1L);
        verify(roleMenuMapper, never()).deleteByMenuId(1L);
    }

    @Test
    void deleteCleansRoleMenuRefs() {
        when(menuMapper.selectCount(any())).thenReturn(0L);

        service.delete(1L);

        verify(menuMapper).deleteById(1L);
        verify(roleMenuMapper).deleteByMenuId(1L);
    }
}
