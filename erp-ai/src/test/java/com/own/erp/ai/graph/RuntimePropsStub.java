package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.config.ErpAlertProperties;
import com.own.erp.contract.SystemConfigApi;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 单测桩(#18 系统设置):SystemConfigApi 返回 null(DB 无覆盖行),
 *         AiRuntimeProperties 全量回落 yml/代码默认值——节点测试继续以 ErpAiProperties 默认配置为基准,
 *         无需起 Spring。DB 覆盖值路径的取值语义单测收口 SystemConfigApiImplTest / AiRuntimePropertiesTest
 */
public final class RuntimePropsStub {

    private RuntimePropsStub() {
    }

    /** 默认桩:DB 无任何覆盖行,全量走 yml 默认 */
    public static AiRuntimeProperties of(ErpAiProperties props) {
        return new AiRuntimeProperties(props, new ErpAlertProperties(), key -> null);
    }

    /** alert 侧桩:预警默认值与生产同源 ErpAlertProperties,DB 无覆盖行 */
    public static AiRuntimeProperties of(ErpAiProperties props, ErpAlertProperties alertProps) {
        return new AiRuntimeProperties(props, alertProps, key -> null);
    }

    /** 自定义覆盖桩:按键返回指定值,未登记键返回 null(回落默认) */
    public static AiRuntimeProperties of(ErpAiProperties props, java.util.Map<String, String> overrides) {
        return new AiRuntimeProperties(props, new ErpAlertProperties(), overrides::get);
    }
}
