package com.own.erp.ai.agent;

import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : Agent 角色词表(四期 agent/):客服全量只读工具;运营收敛到库存/商品盘面工具
 *     (工具名 = tools/ 各 @Tool 方法名,空集 = 全量)。新角色随工具面扩容在此登记,禁散落字面量(docs/07 §1)
 */
public enum AgentRole {

    /** 客服助手:订单/库存/商品/售后全量只读查询 */
    SUPPORT(Set.of()),

    /** 运营助手:库存 + 商品盘面(订单/售后工具不开放) */
    OPS(Set.of("queryInventory", "searchProducts", "findSkuByCode"));

    /** 可用工具名白名单(空集 = 全量) */
    private final Set<String> allowedTools;

    AgentRole(Set<String> allowedTools) {
        this.allowedTools = allowedTools;
    }

    public boolean allows(String toolName) {
        return allowedTools.isEmpty() || allowedTools.contains(toolName);
    }

    /** 路径参数解析:大小写无关(support/OPS 均可),未知角色友好报错(Spring 默认枚举转换大小写敏感) */
    public static AgentRole fromPath(String raw) {
        try {
            return AgentRole.valueOf(cn.hutool.core.util.StrUtil.trim(raw).toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new com.own.erp.common.exception.BusinessException("未知 Agent 角色:" + raw);
        }
    }
}
