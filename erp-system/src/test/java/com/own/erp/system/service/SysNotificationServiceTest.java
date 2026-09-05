package com.own.erp.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.entity.SysNotification;
import com.own.erp.system.mapper.SysNotificationMapper;
import com.own.erp.system.request.query.SysNotificationQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : SysNotificationService 单测(AIR:mock Mapper/用户服务,不依赖数据库),
 *     覆盖告警扇出/内容截断/归属校验/已读状态(docs/07 §10)
 */
class SysNotificationServiceTest {

    private SysNotificationMapper notificationMapper;
    private SysUserService sysUserService;
    private SysNotificationService service;

    @BeforeEach
    void setUp() {
        notificationMapper = mock(SysNotificationMapper.class);
        sysUserService = mock(SysUserService.class);
        service = new SysNotificationService(notificationMapper, sysUserService);
    }

    @Test
    void pushAllUsersFansOutToEveryEnabledUser() {
        when(sysUserService.listEnabledUserIds()).thenReturn(List.of(1L, 2L));

        int written = service.pushAllUsers(SysNotificationService.TYPE_PULL_FAIL, "订单拉取连续失败告警",
                "内容", SysNotificationService.BIZ_TYPE_SHOP, 9L);

        assertEquals(2, written);
        ArgumentCaptor<SysNotification> captor = ArgumentCaptor.forClass(SysNotification.class);
        verify(notificationMapper, times(2)).insert(captor.capture());
        List<SysNotification> rows = captor.getAllValues();
        assertEquals(List.of(1L, 2L), rows.stream().map(SysNotification::getUserId).toList());
        SysNotification first = rows.get(0);
        assertEquals(SysNotificationService.TYPE_PULL_FAIL, first.getNotifyType());
        assertEquals(SysNotificationService.BIZ_TYPE_SHOP, first.getBizType());
        assertEquals(9L, first.getBizId());
        assertEquals(0, first.getReadStatus());
        assertNull(first.getReadAt());
    }

    @Test
    void pushAllUsersTruncatesLongContent() {
        when(sysUserService.listEnabledUserIds()).thenReturn(List.of(1L));

        service.pushAllUsers(SysNotificationService.TYPE_PULL_FAIL, "标题", "x".repeat(5000), null, null);

        ArgumentCaptor<SysNotification> captor = ArgumentCaptor.forClass(SysNotification.class);
        verify(notificationMapper).insert(captor.capture());
        // 截断后(1000 + "...")仍在 content 列 VARCHAR(1024) 之内
        assertTrue(captor.getValue().getContent().length() <= 1004);
    }

    @Test
    void pushAllUsersSkipsInsertWhenNoEnabledUser() {
        when(sysUserService.listEnabledUserIds()).thenReturn(List.of());

        assertEquals(0, service.pushAllUsers(SysNotificationService.TYPE_PULL_FAIL, "标题", null, null, null));

        verify(notificationMapper, never()).insert(any(SysNotification.class));
    }

    @Test
    void getOwnedReturnsNullForOtherUsersNotification() {
        when(notificationMapper.selectById(5L))
                .thenReturn(SysNotification.builder().id(5L).userId(77L).build());
        assertNull(service.getOwned(1L, 5L));
        when(notificationMapper.selectById(6L))
                .thenReturn(SysNotification.builder().id(6L).userId(1L).build());
        assertEquals(6L, service.getOwned(1L, 6L).id());
    }

    @Test
    void unreadCountReturnsMapperCount() {
        when(notificationMapper.selectCount(any())).thenReturn(3L);
        assertEquals(3L, service.unreadCount(1L));
    }

    @Test
    void markReadThrowsWhenNoRowUpdated() {
        when(notificationMapper.update(any(), any())).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.markRead(1L, 404L));
        when(notificationMapper.update(any(), any())).thenReturn(1);
        assertDoesNotThrow(() -> service.markRead(1L, 1L));
    }

    @Test
    void markAllReadReturnsAffectedRows() {
        when(notificationMapper.update(any(), any())).thenReturn(4);
        assertEquals(4, service.markAllRead(1L));
    }

    @Test
    void pageMapsRecordsToResponse() {
        Page<SysNotification> page = new Page<>(1, 10);
        page.setRecords(List.of(SysNotification.builder().id(2L).userId(1L).build()));
        doReturn(page).when(notificationMapper).selectPage(any(), any());

        assertEquals(2L, service.page(1L, new SysNotificationQuery()).getRecords().get(0).id());
    }
}
