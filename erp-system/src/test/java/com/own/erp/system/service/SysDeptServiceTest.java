package com.own.erp.system.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.entity.SysDept;
import com.own.erp.system.mapper.SysDeptMapper;
import com.own.erp.system.mapper.SysUserMapper;
import com.own.erp.system.request.command.SysDeptSaveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : SysDeptService 单测(#27③,AIR:mock Mapper,不依赖数据库):
 *     父部门存在性 / 成环校验(自身与自身子孙禁挂,脏数据环自保护)/ 删除前子部门与用户引用拦截 / 树组装
 */
class SysDeptServiceTest {

    private SysDeptMapper deptMapper;
    private SysUserMapper userMapper;
    private SysDeptService deptService;

    @BeforeEach
    void setUp() {
        deptMapper = mock(SysDeptMapper.class);
        userMapper = mock(SysUserMapper.class);
        deptService = new SysDeptService(deptMapper, userMapper);
    }

    private SysDeptSaveRequest request(Long parentId) {
        return SysDeptSaveRequest.builder()
                .parentId(parentId)
                .deptName("部门A")
                .sort(0)
                .status(1)
                .build();
    }

    private SysDept dept(Long id, Long parentId) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setDeptName("部门" + id);
        return dept;
    }

    // ---------- 父部门存在性 ----------

    @Test
    void createDefaultsToRootWhenParentAbsent() {
        deptService.create(SysDeptSaveRequest.builder().deptName("根部门").build());

        verify(deptMapper).insert(any(SysDept.class));
    }

    @Test
    void createRejectedWhenParentNotExist() {
        when(deptMapper.selectById(9L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> deptService.create(request(9L)));

        assertTrue(e.getMessage().contains("父部门不存在"));
        verify(deptMapper, never()).insert(any(SysDept.class));
    }

    @Test
    void createAllowedWhenParentExists() {
        when(deptMapper.selectById(9L)).thenReturn(dept(9L, 0L));

        deptService.create(request(9L));

        verify(deptMapper).insert(any(SysDept.class));
    }

    @Test
    void updateRejectedWhenParentNotExist() {
        when(deptMapper.selectById(9L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> deptService.update(5L, request(9L)));

        verify(deptMapper, never()).updateById(any(SysDept.class));
    }

    // ---------- 成环校验 ----------

    @Test
    void updateRejectedWhenParentIsSelf() {
        BusinessException e = assertThrows(BusinessException.class, () -> deptService.update(5L, request(5L)));

        assertTrue(e.getMessage().contains("自己或自己的子孙"));
        verify(deptMapper, never()).updateById(any(SysDept.class));
    }

    @Test
    void updateRejectedWhenHangingUnderOwnDescendant() {
        // 链:6 的父是 5(被更新节点)。把 5 挂到 6 下 = 挂到自己子孙下,成环
        when(deptMapper.selectById(6L)).thenReturn(dept(6L, 5L));

        BusinessException e = assertThrows(BusinessException.class, () -> deptService.update(5L, request(6L)));

        assertTrue(e.getMessage().contains("自己或自己的子孙"));
        verify(deptMapper, never()).updateById(any(SysDept.class));
    }

    @Test
    void updateRejectedWhenExistingDataAlreadyCyclic() {
        // 存量脏数据:7↔8 互为父子。把新部门挂上时不死循环,直接拒
        when(deptMapper.selectById(7L)).thenReturn(dept(7L, 8L));
        when(deptMapper.selectById(8L)).thenReturn(dept(8L, 7L));

        BusinessException e = assertThrows(BusinessException.class, () -> deptService.create(request(7L)));

        assertTrue(e.getMessage().contains("环"));
        verify(deptMapper, never()).insert(any(SysDept.class));
    }

    @Test
    void updateAllowedWhenReparentToRoot() {
        deptService.update(5L, request(0L));

        verify(deptMapper).updateById(any(SysDept.class));
    }

    @Test
    void updateAllowedWhenParentChainClean() {
        // 链:6 的父是 0。5 挂到 6 下合法
        when(deptMapper.selectById(6L)).thenReturn(dept(6L, 0L));

        deptService.update(5L, request(6L));

        verify(deptMapper).updateById(any(SysDept.class));
    }

    // ---------- 删除引用校验 ----------

    @Test
    void deleteRejectedWhenChildDeptExists() {
        when(deptMapper.selectCount(any())).thenReturn(2L);

        BusinessException e = assertThrows(BusinessException.class, () -> deptService.delete(5L));

        assertTrue(e.getMessage().contains("子部门"));
        verify(deptMapper, never()).deleteById(5L);
    }

    @Test
    void deleteRejectedWhenUserReferenced() {
        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(3L);

        BusinessException e = assertThrows(BusinessException.class, () -> deptService.delete(5L));

        assertTrue(e.getMessage().contains("用户"));
        verify(deptMapper, never()).deleteById(5L);
    }

    @Test
    void deleteAllowedWhenNoChildAndNoUserRef() {
        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(0L);

        deptService.delete(5L);

        verify(deptMapper).deleteById(5L);
    }

    // ---------- 树组装 ----------

    @Test
    void treeNestsChildrenUnderParents() {
        SysDept root = dept(1L, 0L);
        SysDept child = dept(2L, 1L);
        when(deptMapper.selectList(any())).thenReturn(List.of(root, child));

        List<SysDeptService.DeptNode> tree = deptService.tree();

        assertEquals(1, tree.size());
        assertEquals(1L, tree.get(0).id());
        assertEquals(1, tree.get(0).children().size());
        assertEquals(2L, tree.get(0).children().get(0).id());
        assertTrue(tree.get(0).children().get(0).children().isEmpty());
    }
}
