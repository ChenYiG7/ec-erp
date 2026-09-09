package com.own.erp.system.service;

import com.own.erp.common.api.SystemConfigChangedEvent;
import com.own.erp.common.constant.ConfigConsts;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.entity.SysConfig;
import com.own.erp.system.mapper.SysConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : SystemConfigService 单测(#18,AIR:mock Mapper/事件发布器,不依赖数据库):
 *     valueOf 空值语义、listByGroup 合并视图(词表全量 × DB 已存行)、saveGroup 三重校验
 *     (词表白名单/类型可解析/长度钳制)、空值删覆盖行、保存后发布变更事件。eq 路径可直测,
 *     禁 .in()(docs/07 §10 急切解析坑)
 */
class SystemConfigServiceTest {

    private static final String KEY_INT = ConfigConsts.KEY_REPLENISH_COVERAGE_DAYS;
    private static final String KEY_DECIMAL = ConfigConsts.KEY_ANOMALY_BIG_ORDER_AMOUNT;
    private static final String KEY_BOOL = ConfigConsts.KEY_ALERT_ENABLED;
    private static final String KEY_TEXT = ConfigConsts.KEY_SYSTEM_PROMPT;
    private static final String KEY_SECRET = ConfigConsts.KEY_MAIL_PASSWORD;
    private static final String KEY_MAIL_BOOL = ConfigConsts.KEY_MAIL_ENABLED;

    private SysConfigMapper configMapper;
    private ApplicationEventPublisher eventPublisher;
    private SystemConfigService service;

    @BeforeEach
    void setUp() {
        configMapper = mock(SysConfigMapper.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new SystemConfigService(configMapper, eventPublisher);
    }

    @Test
    void valueOfReturnsNullWhenRowMissing() {
        when(configMapper.selectOne(any())).thenReturn(null);
        assertNull(service.valueOf(KEY_INT));
    }

    @Test
    void valueOfReturnsBlankAsNull() {
        when(configMapper.selectOne(any())).thenReturn(SysConfig.builder()
                .configKey(KEY_INT).configValue("  ").build());
        assertNull(service.valueOf(KEY_INT));
    }

    @Test
    void listByGroupReturnsFullWordlistMergedWithSavedRows() {
        // DB 已存 1 行(其余词表键无行 → 占位行返回,前端表单渲染代码默认值)
        when(configMapper.selectList(any())).thenReturn(List.of(SysConfig.builder()
                .id(1L).configGroup(ConfigConsts.GROUP_AI)
                .configKey(KEY_DECIMAL).configValue("20000").build()));
        List<SysConfig> rows = service.listByGroup(ConfigConsts.GROUP_AI);
        assertEquals(ConfigConsts.AI_KEYS.size(), rows.size());
        // 已存行透传
        assertTrue(rows.stream().anyMatch(r -> KEY_DECIMAL.equals(r.getConfigKey())
                && "20000".equals(r.getConfigValue())));
        // 无行键只给占位(key/group 有值,value 为空)
        assertTrue(rows.stream().anyMatch(r -> KEY_INT.equals(r.getConfigKey())
                && r.getConfigValue() == null));
    }

    @Test
    void listByGroupRejectsUnknownGroup() {
        assertThrows(BusinessException.class, () -> service.listByGroup("NOPE"));
    }

    @Test
    void saveGroupInsertsNewKeyWithGroupBackfill() {
        when(configMapper.selectOne(any())).thenReturn(null);
        when(configMapper.insert(any(SysConfig.class))).thenReturn(1);
        int affected = service.saveGroup(ConfigConsts.GROUP_AI, Map.of(KEY_INT, "21"));
        assertEquals(1, affected);
        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(configMapper).insert(captor.capture());
        assertEquals(ConfigConsts.GROUP_AI, captor.getValue().getConfigGroup());
        assertEquals(KEY_INT, captor.getValue().getConfigKey());
        assertEquals("21", captor.getValue().getConfigValue());
        verify(eventPublisher).publishEvent(any(SystemConfigChangedEvent.class));
    }

    @Test
    void saveGroupUpdatesExistingKey() {
        when(configMapper.selectOne(any())).thenReturn(SysConfig.builder()
                .id(5L).configGroup(ConfigConsts.GROUP_AI).configKey(KEY_INT).configValue("14").build());
        when(configMapper.updateById(any(SysConfig.class))).thenReturn(1);
        int affected = service.saveGroup(ConfigConsts.GROUP_AI, Map.of(KEY_INT, "21"));
        assertEquals(1, affected);
        verify(configMapper).updateById(any(SysConfig.class));
        verify(configMapper, never()).insert(any(SysConfig.class));
    }

    @Test
    void saveGroupBlankValueDeletesOverrideRow() {
        when(configMapper.selectOne(any())).thenReturn(SysConfig.builder()
                .id(5L).configGroup(ConfigConsts.GROUP_AI).configKey(KEY_INT).configValue("14").build());
        when(configMapper.deleteById(5L)).thenReturn(1);
        int affected = service.saveGroup(ConfigConsts.GROUP_AI, Map.of(KEY_INT, "  "));
        assertEquals(1, affected);
        verify(configMapper).deleteById(5L);
    }

    @Test
    void saveGroupRejectsUnknownKey() {
        assertThrows(BusinessException.class,
                () -> service.saveGroup(ConfigConsts.GROUP_AI, Map.of("erp.hack.key", "1")));
        verify(configMapper, never()).insert(any(SysConfig.class));
    }

    @Test
    void saveGroupRejectsCrossGroupKey() {
        // ALERT 键提交进 AI 组 = 组键不匹配
        assertThrows(BusinessException.class,
                () -> service.saveGroup(ConfigConsts.GROUP_AI, Map.of(KEY_BOOL, "true")));
    }

    @Test
    void saveGroupRejectsUnparsableValue() {
        assertThrows(BusinessException.class,
                () -> service.saveGroup(ConfigConsts.GROUP_AI, Map.of(KEY_INT, "abc")));
        assertThrows(BusinessException.class,
                () -> service.saveGroup(ConfigConsts.GROUP_AI, Map.of(KEY_DECIMAL, "1.2.3")));
        assertThrows(BusinessException.class,
                () -> service.saveGroup(ConfigConsts.GROUP_AI, Map.of(KEY_BOOL, "yes")));
    }

    @Test
    void saveGroupRejectsOverlongValue() {
        assertThrows(BusinessException.class,
                () -> service.saveGroup(ConfigConsts.GROUP_AI, Map.of(KEY_TEXT, "长".repeat(1025))));
    }

    @Test
    void saveGroupAcceptsMultiKeyBatch() {
        when(configMapper.selectOne(any())).thenReturn(null);
        when(configMapper.insert(any(SysConfig.class))).thenReturn(1);
        int affected = service.saveGroup(ConfigConsts.GROUP_ALERT, Map.of(KEY_BOOL, "true",
                ConfigConsts.KEY_ALERT_LOW_STOCK_THRESHOLD, "5"));
        assertEquals(2, affected);
    }

    @Test
    void listByGroupMasksSecretValues() {
        // SECRET 键已存值回显掩码(真值不出后端);占位行(无 DB 行)value 恒 null 不受影响
        when(configMapper.selectList(any())).thenReturn(List.of(SysConfig.builder()
                .id(9L).configGroup(ConfigConsts.GROUP_NOTIFY)
                .configKey(KEY_SECRET).configValue("real-auth-code").build()));
        List<SysConfig> rows = service.listByGroup(ConfigConsts.GROUP_NOTIFY);
        assertEquals(ConfigConsts.NOTIFY_KEYS.size(), rows.size());
        assertTrue(rows.stream().anyMatch(r -> KEY_SECRET.equals(r.getConfigKey())
                && ConfigConsts.SECRET_MASK.equals(r.getConfigValue())));
        assertTrue(rows.stream().anyMatch(r -> ConfigConsts.KEY_MAIL_HOST.equals(r.getConfigKey())
                && r.getConfigValue() == null));
    }

    @Test
    void saveGroupSecretMaskPassthroughSkipsWhenRowExists() {
        // 前端把回显掩码原样提交回来 = 未改动,DB 已有行时跳过,真值不被掩码覆盖(防邮件静默失效)
        SysConfig existing = SysConfig.builder()
                .id(9L).configGroup(ConfigConsts.GROUP_NOTIFY)
                .configKey(KEY_SECRET).configValue("real-auth-code").build();
        when(configMapper.selectOne(any())).thenReturn(existing);
        int affected = service.saveGroup(ConfigConsts.GROUP_NOTIFY, Map.of(KEY_SECRET, ConfigConsts.SECRET_MASK));
        assertEquals(0, affected);
        verify(configMapper, never()).insert(any(SysConfig.class));
        verify(configMapper, never()).updateById(any(SysConfig.class));
        verify(configMapper, never()).deleteById(any(Long.class));
        // 跳过仍发布变更事件(同组其他键可能生效,缓存统一失效)
        verify(eventPublisher).publishEvent(any(SystemConfigChangedEvent.class));
    }

    @Test
    void saveGroupSecretMaskWithoutRowStoresLiteral() {
        // DB 无行时掩码字面量按普通新值落库(防真密码恰为掩码字面量的碰撞被误吞)
        when(configMapper.selectOne(any())).thenReturn(null);
        when(configMapper.insert(any(SysConfig.class))).thenReturn(1);
        int affected = service.saveGroup(ConfigConsts.GROUP_NOTIFY, Map.of(KEY_SECRET, ConfigConsts.SECRET_MASK));
        assertEquals(1, affected);
        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(configMapper).insert(captor.capture());
        assertEquals(ConfigConsts.GROUP_NOTIFY, captor.getValue().getConfigGroup());
        assertEquals(ConfigConsts.SECRET_MASK, captor.getValue().getConfigValue());
    }

    @Test
    void saveGroupNotifyKeysRouteAndValidate() {
        // NOTIFY 组:BOOL/INT 键类型校验生效;SECRET 键真值正常落库
        when(configMapper.selectOne(any())).thenReturn(null);
        when(configMapper.insert(any(SysConfig.class))).thenReturn(1);
        int affected = service.saveGroup(ConfigConsts.GROUP_NOTIFY,
                Map.of(KEY_MAIL_BOOL, "true", ConfigConsts.KEY_MAIL_PORT, "465", KEY_SECRET, "auth-code"));
        assertEquals(3, affected);
        assertThrows(BusinessException.class,
                () -> service.saveGroup(ConfigConsts.GROUP_NOTIFY, Map.of(KEY_MAIL_BOOL, "yes")));
        assertThrows(BusinessException.class,
                () -> service.saveGroup(ConfigConsts.GROUP_NOTIFY, Map.of(ConfigConsts.KEY_MAIL_PORT, "smtp")));
        assertThrows(BusinessException.class,
                () -> service.saveGroup(ConfigConsts.GROUP_NOTIFY, Map.of(KEY_BOOL, "true")));
    }

    @Test
    void valueOfReturnsSecretRealValueForConsumerSide() {
        // 消费侧(MailPushService)走 valueOf 取真值,与读侧脱敏零耦合
        when(configMapper.selectOne(any())).thenReturn(SysConfig.builder()
                .configKey(KEY_SECRET).configValue("real-auth-code").build());
        assertEquals("real-auth-code", service.valueOf(KEY_SECRET));
    }
}
