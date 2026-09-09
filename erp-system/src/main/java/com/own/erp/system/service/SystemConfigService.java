package com.own.erp.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.own.erp.common.api.SystemConfigChangedEvent;
import com.own.erp.common.constant.ConfigConsts;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.entity.SysConfig;
import com.own.erp.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 系统参数服务(#18 系统设置):sys_config 域唯一写入口(docs/07 §2.1 收口 Service)。
 *     读侧:按键取覆盖值(null=走代码默认)/分组全量(合并视图,前端表单渲染);
 *     写侧:保存前三重校验——①键必须在 ConfigConsts 词表白名单(防乱键注入)②组与键匹配(防跨组错位)
 *     ③值类型可解析 + 长度钳制(文本键放行 ≤1024,数字/布尔键解析失败拒存——配置错误在保存口暴露,禁带病落库);
 *     保存即 upsert(uk_config_key 兜底)并返回生效键数。类型词表与消费侧解析器同源本类,
 *     新键登记 ConfigConsts + typeOf 补一行即可(单点扩容)。
 *     凭证类键(openai api-key 等)不在词表——词表白名单天然拦截,凭证只走环境变量/local.properties(docs/07 §7);
 *     ⚠️ 唯一例外 = SMTP 授权码(SECRET 类型,#14 邮箱渠道 2026-09-08 拍板):
 *     读侧 listByGroup 对 SECRET 非空值统一回显 SECRET_MASK——真值不出后端(读侧 isAuthenticated 亦不泄);
 *     写侧 saveGroup 收到 SECRET_MASK 视为"未改动"跳过(防掩码回写覆盖真值致邮件静默失效),
 *     仅当 DB 无行时才把提交值按字面落库(防真密码恰为掩码字面量的碰撞);消费侧 valueOf 恒取真值零感知
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SystemConfigService {

    /** 参数值类型词表(校验用;PROMPT/URL 为文本放行) */
    private enum ValueType {
        /** 整数 */
        INT,
        /** 长整数 */
        LONG,
        /** 十进制数(阈值/比率) */
        DECIMAL,
        /** 布尔(开关类) */
        BOOL,
        /** 自由文本(prompt/base-url) */
        TEXT,
        /** 敏感凭证(校验同 TEXT;读侧回显脱敏,写侧掩码回环跳过——SMTP 授权码专用) */
        SECRET
    }

    private final SysConfigMapper configMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** 按键取 DB 覆盖值(文本原样,trim 后空白视为无值);无行/空值返回 null,消费侧回落代码默认值 */
    public String valueOf(String configKey) {
        SysConfig row = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey));
        return row == null ? null : StrUtil.trimToNull(row.getConfigValue());
    }

    /** 分组全量:该组合法键全词表 × DB 已存行(合并视图,DB 无行 = 代码默认值,前端表单仍要渲染);
     *  已存行按组 eq 查 + 内存交集(禁 .in() 急切解析坑,docs/07 §10);
     *  SECRET 键已存值统一回显掩码(真值不出后端;占位行本就无值,不受影响) */
    public List<SysConfig> listByGroup(String configGroup) {
        Set<String> keys = keysOfGroup(configGroup);
        Map<String, SysConfig> savedBy = configMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigGroup, configGroup))
                .stream()
                .filter(row -> keys.contains(row.getConfigKey()))
                .collect(Collectors.toMap(SysConfig::getConfigKey, Function.identity()));
        List<SysConfig> rows = keys.stream()
                .map(key -> savedBy.containsKey(key) ? savedBy.get(key)
                        : SysConfig.builder().configGroup(configGroup).configKey(key).build())
                .sorted(java.util.Comparator.comparing(SysConfig::getConfigKey))
                .toList();
        rows.forEach(row -> {
            if (typeOf(row.getConfigKey()) == ValueType.SECRET && StrUtil.isNotBlank(row.getConfigValue())) {
                row.setConfigValue(ConfigConsts.SECRET_MASK);
            }
        });
        return rows;
    }

    /**
     * 保存一组参数(upsert):逐键三重校验(词表白名单/组键匹配/类型可解析),全部通过才落库;
     * 空值 = 删除覆盖行(回落代码默认值),返回实际生效(写入/删除)键数;
     * SECRET 键提交掩码 = 未改动跳过(DB 已有行)——防前端把回显掩码原样提交回来覆盖真值;
     * DB 无行时的掩码字面量按普通新值落库(防真密码恰为掩码字面量的碰撞被误吞)
     */
    public int saveGroup(String configGroup, Map<String, String> values) {
        Set<String> allowedKeys = keysOfGroup(configGroup);
        int affected = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = StrUtil.trim(entry.getKey());
            String value = StrUtil.trimToNull(entry.getValue());
            validate(configGroup, allowedKeys, key, value);
            if (typeOf(key) == ValueType.SECRET && ConfigConsts.SECRET_MASK.equals(value)
                    && configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getConfigKey, key)) != null) {
                continue;
            }
            affected += upsert(key, value);
        }
        // 保存后发布变更事件:缓存持有方(SystemConfigApiImpl)即时失效,模型/提示词改动秒级生效
        eventPublisher.publishEvent(new SystemConfigChangedEvent(configGroup, values.keySet()));
        log.info("系统参数保存:组 {},提交 {} 键,生效 {} 键", configGroup, values.size(), affected);
        return affected;
    }

    /** 校验:词表白名单 → 组键匹配 → 类型可解析(空值放行 = 删覆盖行) */
    private void validate(String configGroup, Set<String> allowedKeys, String key, String value) {
        if (StrUtil.isBlank(key)) {
            throw new BusinessException("参数键不能为空");
        }
        if (!allowedKeys.contains(key)) {
            throw new BusinessException("未知参数键或组不匹配:" + key);
        }
        if (value == null) {
            return;
        }
        if (value.length() > ConfigConsts.VALUE_MAX_LENGTH) {
            throw new BusinessException("参数值超长(≤" + ConfigConsts.VALUE_MAX_LENGTH + " 字):" + key);
        }
        ValueType type = typeOf(key);
        try {
            switch (type) {
                case INT -> Integer.parseInt(value);
                case LONG -> Long.parseLong(value);
                case DECIMAL -> new BigDecimal(value);
                case BOOL -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new NumberFormatException(value);
                    }
                }
                case TEXT -> {
                    // 自由文本放行
                }
                case SECRET -> {
                    // 敏感凭证(授权码)无格式约束,长度校验已在上方统一执行
                }
            }
        } catch (NumberFormatException e) {
            throw new BusinessException("参数值不是合法的 " + type + ":" + key);
        }
    }

    /** upsert 单键:uk_config_key 冲突走 update;空值删行(回落默认) */
    private int upsert(String key, String value) {
        SysConfig existing = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
        if (value == null) {
            return existing == null ? 0 : configMapper.deleteById(existing.getId());
        }
        if (existing == null) {
            String group = groupOfKey(key);
            configMapper.insert(SysConfig.builder()
                    .configGroup(group).configKey(key).configValue(value).build());
            return 1;
        }
        existing.setConfigValue(value);
        return configMapper.updateById(existing);
    }

    /** 组 → 合法键集(词表唯一事实源 ConfigConsts) */
    private Set<String> keysOfGroup(String configGroup) {
        return switch (StrUtil.nullToEmpty(configGroup)) {
            case ConfigConsts.GROUP_AI -> ConfigConsts.AI_KEYS;
            case ConfigConsts.GROUP_ALERT -> ConfigConsts.ALERT_KEYS;
            case ConfigConsts.GROUP_SALES -> ConfigConsts.SALES_KEYS;
            case ConfigConsts.GROUP_NOTIFY -> ConfigConsts.NOTIFY_KEYS;
            default -> throw new BusinessException("未知参数组:" + configGroup);
        };
    }

    /** 键 → 归属组(插入行回填组用) */
    private String groupOfKey(String key) {
        if (ConfigConsts.AI_KEYS.contains(key)) {
            return ConfigConsts.GROUP_AI;
        }
        if (ConfigConsts.ALERT_KEYS.contains(key)) {
            return ConfigConsts.GROUP_ALERT;
        }
        if (ConfigConsts.NOTIFY_KEYS.contains(key)) {
            return ConfigConsts.GROUP_NOTIFY;
        }
        return ConfigConsts.GROUP_SALES;
    }

    /** 键 → 值类型(校验用;新增键在此补一行,与消费侧解析器同源) */
    private ValueType typeOf(String key) {
        return switch (key) {
            case ConfigConsts.KEY_REPLENISH_LOW_STOCK_THRESHOLD,
                 ConfigConsts.KEY_REPLENISH_COVERAGE_DAYS,
                 ConfigConsts.KEY_REPLENISH_SALES_WINDOW_DAYS,
                 ConfigConsts.KEY_REPLENISH_MIN_SUGGEST_QTY,
                 ConfigConsts.KEY_ANOMALY_LLM_MAX_ITEMS,
                 ConfigConsts.KEY_AGENT_MAX_ITERS,
                 ConfigConsts.KEY_AGENT_HISTORY_MAX_MESSAGES,
                 ConfigConsts.KEY_TOOL_AUDIT_MAX_LENGTH,
                 ConfigConsts.KEY_ALERT_QUIET_HOURS,
                 ConfigConsts.KEY_ALERT_LOW_STOCK_THRESHOLD,
                 ConfigConsts.KEY_ALERT_SHIP_TIMEOUT_HOURS,
                 ConfigConsts.KEY_ALERT_REFUND_WINDOW_HOURS,
                 ConfigConsts.KEY_ALERT_REFUND_COUNT_THRESHOLD,
                 ConfigConsts.KEY_ALERT_TOP_N,
                 ConfigConsts.KEY_ALERT_SLOW_MOVING_DAYS,
                 ConfigConsts.KEY_ALERT_OVERSTOCK_DAYS,
                 ConfigConsts.KEY_SALES_REBUILD_DAYS,
                 ConfigConsts.KEY_ANOMALY_UNPAID_HOURS -> ValueType.INT;
            case ConfigConsts.KEY_ALERT_ENABLED,
                 ConfigConsts.KEY_SALES_ENABLED -> ValueType.BOOL;
            case ConfigConsts.KEY_ANOMALY_BIG_ORDER_AMOUNT,
                 ConfigConsts.KEY_ANOMALY_HIGH_DISCOUNT_RATIO -> ValueType.DECIMAL;
            case ConfigConsts.KEY_SYSTEM_PROMPT,
                 ConfigConsts.KEY_REPLENISH_SUMMARY_PROMPT,
                 ConfigConsts.KEY_ANOMALY_SCORE_PROMPT,
                 ConfigConsts.KEY_AGENT_SUPPORT_PROMPT,
                 ConfigConsts.KEY_AGENT_OPS_PROMPT,
                 ConfigConsts.KEY_MODEL,
                 ConfigConsts.KEY_AGENT_BASE_URL,
                 ConfigConsts.KEY_AGENT_MODEL -> ValueType.TEXT;
            case ConfigConsts.KEY_MAIL_ENABLED,
                 ConfigConsts.KEY_MAIL_SSL -> ValueType.BOOL;
            case ConfigConsts.KEY_MAIL_PORT -> ValueType.INT;
            case ConfigConsts.KEY_MAIL_PASSWORD -> ValueType.SECRET;
            default -> ValueType.TEXT;
        };
    }
}
