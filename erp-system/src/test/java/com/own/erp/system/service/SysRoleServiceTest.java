package com.own.erp.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.PageQuery;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.entity.SysRole;
import com.own.erp.system.mapper.SysRoleMapper;
import com.own.erp.system.mapper.SysRoleMenuMapper;
import com.own.erp.system.mapper.SysUserRoleMapper;
import com.own.erp.system.request.command.SysRoleSaveRequest;
import com.own.erp.system.response.SysRoleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/5
 * @Description : SysRoleService 单测(AIR:mock Mapper,docs/07 §10)。
 *     重点:删角色前置校验(仍绑用户即拒)+ 同事务清理角色-菜单绑定
 */
class SysRoleServiceTest {

    private SysRoleMapper roleMapper;
    private SysUserRoleMapper userRoleMapper;
    private SysRoleMenuMapper roleMenuMapper;
    private SysRoleService service;

    @BeforeEach
    void setUp() {
        roleMapper = mock(SysRoleMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        roleMenuMapper = mock(SysRoleMenuMapper.class);
        service = new SysRoleService(roleMapper, userRoleMapper, roleMenuMapper);
    }

    @Test
    void createRoleInsertsMappedEntity() {
        Long id = service.createRole(SysRoleSaveRequest.builder()
                .roleName("运营").roleKey("operator").status(1).remark("备注").build());

        ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
        verify(roleMapper).insert(captor.capture());
        SysRole row = captor.getValue();
        assertEquals("运营", row.getRoleName());
        assertEquals("operator", row.getRoleKey());
        assertEquals(1, row.getStatus());
        assertEquals("备注", row.getRemark());
        // id 由数据库自增回填,mock 环境无 keygen
        assertEquals(id, row.getId());
    }

    @Test
    void updateRoleSetsIdAndMapsFields() {
        service.updateRole(3L, SysRoleSaveRequest.builder()
                .roleName("运营").roleKey("operator").status(0).build());

        ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
        verify(roleMapper).updateById(captor.capture());
        assertEquals(3L, captor.getValue().getId());
        assertEquals(0, captor.getValue().getStatus());
    }

    @Test
    void pageRolesMapsRecordsToResponse() {
        Page<SysRole> page = new Page<>(1, 10);
        page.setRecords(List.of(SysRole.builder().id(1L).roleName("管理员").roleKey("admin").build()));
        doReturn(page).when(roleMapper).selectPage(any(), any());

        Page<SysRoleResponse> responsePage = service.pageRoles(new PageQuery());
        assertEquals("admin", responsePage.getRecords().get(0).roleKey());
    }

    @Test
    void deleteRoleRejectsWhileUsersBound() {
        when(userRoleMapper.countByRoleId(4L)).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteRole(4L));

        assertTrue(ex.getMessage().contains("2"));
        verify(roleMapper, never()).deleteById(4L);
        verify(roleMenuMapper, never()).deleteByRoleId(4L);
    }

    @Test
    void deleteRoleCleansMenuBindings() {
        when(userRoleMapper.countByRoleId(4L)).thenReturn(0L);

        service.deleteRole(4L);

        verify(roleMapper).deleteById(4L);
        verify(roleMenuMapper).deleteByRoleId(4L);
    }
}
