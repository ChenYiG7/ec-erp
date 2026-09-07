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
}
