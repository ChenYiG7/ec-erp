package com.own.erp.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.entity.SysUser;
import com.own.erp.system.mapper.SysUserMapper;
import com.own.erp.system.mapper.SysUserRoleMapper;
import com.own.erp.system.request.command.SysUserSaveRequest;
import com.own.erp.system.request.query.SysUserQuery;
import com.own.erp.system.response.SysUserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * @Description : SysUserService 单测(AIR:mock Mapper,PasswordEncoder 用真实 BCrypt 低代价轮次,
 *     顺带验证 matches 真实哈希;docs/07 §10)。重点:三条专用密码通道(#1)/改用户名唯一校验/
 *     更新路径 password 物理隔绝(toEntity 不映射)
 */
class SysUserServiceTest {

    private static final String RAW = "raw-Passw0rd";

    private SysUserMapper userMapper;
    private SysUserRoleMapper userRoleMapper;
    private PasswordEncoder passwordEncoder;
    private SysUserService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        // 真实 BCrypt(低代价轮次提速):加密/比对走真算法,非打桩自嗨
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new SysUserService(userMapper, userRoleMapper, passwordEncoder);
    }

    private SysUserSaveRequest request(String password) {
        return SysUserSaveRequest.builder().username("chen").password(password)
                .nickname("陈").email("c@x.com").phone("138").status(1).build();
    }

    @Test
    void createUserRejectsBlankPassword() {
        assertThrows(BusinessException.class, () -> service.createUser(request(null)));
        assertThrows(BusinessException.class, () -> service.createUser(request("  ")));
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createUser(request(RAW)));

        assertTrue(ex.getMessage().contains("chen"));
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void createUserEncodesPasswordOnInsert() {
        when(userMapper.selectCount(any())).thenReturn(0L);

        service.createUser(request(RAW));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        SysUser row = captor.getValue();
        assertEquals("chen", row.getUsername());
        assertEquals("陈", row.getNickname());
        assertEquals(1, row.getStatus());
        // 落库的是 BCrypt 哈希而非明文,且哈希确实能对上原文
        assertNotEquals(RAW, row.getPassword());
        assertTrue(passwordEncoder.matches(RAW, row.getPassword()));
    }

    @Test
    void updateUserRejectsDuplicateUsername() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.updateUser(7L, request(RAW)));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void updateUserNeverCarriesPassword() {
        when(userMapper.selectCount(any())).thenReturn(0L);

        service.updateUser(7L, request(RAW));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        SysUser row = captor.getValue();
        assertEquals(7L, row.getId());
        // password 不在 toEntity 映射内:更新路径物理隔绝,MP null-skip 不会碰密码列
        assertNull(row.getPassword());
    }

    @Test
    void getUserByIdReturnsNullWhenAbsent() {
        when(userMapper.selectById(404L)).thenReturn(null);
        assertNull(service.getUserById(404L));
    }

    @Test
    void getUserByIdMapsResponseWithoutPassword() {
        when(userMapper.selectById(7L)).thenReturn(SysUser.builder().id(7L).username("chen")
                .password("bcrypt-hash").nickname("陈").status(1).build());

        SysUserResponse response = service.getUserById(7L);
        assertEquals(7L, response.id());
        assertEquals("chen", response.username());
        assertEquals("陈", response.nickname());
        // record 组件无 password 字段(编译期封死),任何返回路径不泄漏哈希
    }

    @Test
    void resetPasswordRejectsBlank() {
        assertThrows(BusinessException.class, () -> service.resetPassword(7L, null));
        assertThrows(BusinessException.class, () -> service.resetPassword(7L, ""));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void resetPasswordEncodesAndUpdates() {
        service.resetPassword(7L, RAW);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(7L, captor.getValue().getId());
        assertTrue(passwordEncoder.matches(RAW, captor.getValue().getPassword()));
    }

    @Test
    void changePasswordRejectsWhenUserMissing() {
        when(userMapper.selectById(404L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changePassword(404L, "old", "new-Pass"));
        assertEquals(401, ex.getCode());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        when(userMapper.selectById(7L))
                .thenReturn(SysUser.builder().id(7L).password(passwordEncoder.encode("real-old")).build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changePassword(7L, "wrong-old", "new-Pass"));
        assertEquals(401, ex.getCode());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void changePasswordUpdatesWithEncodedNewPassword() {
        when(userMapper.selectById(7L))
                .thenReturn(SysUser.builder().id(7L).password(passwordEncoder.encode("real-old")).build());

        service.changePassword(7L, "real-old", RAW);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertTrue(passwordEncoder.matches(RAW, captor.getValue().getPassword()));
    }

    @Test
    void deleteUserCleansRoleBindings() {
        service.deleteUser(7L);
        verify(userMapper).deleteById(7L);
        verify(userRoleMapper).deleteByUserId(7L);
    }

    @Test
    void listEnabledUserIdsReturnsMapperIds() {
        when(userMapper.selectList(any())).thenReturn(List.of(
                SysUser.builder().id(1L).build(), SysUser.builder().id(2L).build()));

        assertEquals(List.of(1L, 2L), service.listEnabledUserIds());
    }

    @Test
    void pageUsersMapsRecordsToResponse() {
        Page<SysUser> page = new Page<>(1, 10);
        page.setRecords(List.of(SysUser.builder().id(7L).username("chen").build()));
        doReturn(page).when(userMapper).selectPage(any(), any());

        Page<SysUserResponse> responsePage = service.pageUsers(new SysUserQuery());
        assertEquals(1, responsePage.getRecords().size());
        assertEquals("chen", responsePage.getRecords().get(0).username());
    }
}
