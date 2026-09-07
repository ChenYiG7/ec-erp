package com.own.erp.ai.constant;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI 域常量(ai_suggestion 确认状态/类型/风险词表 + ai_chat_message 角色词表):
 *         禁魔法值(docs/07 §1);Mapper cas SQL 中的状态字面量与本类保持同步(同 AftersaleConsts 先例)
 */
public final class AiConsts {

    /** 建议确认状态:0待确认(cas 守卫前置态) */
    public static final int STATUS_PENDING = 0;

    /** 建议确认状态:1已采纳 */
    public static final int STATUS_ADOPTED = 1;

    /** 建议确认状态:2已忽略 */
    public static final int STATUS_IGNORED = 2;

    /** 建议类型:补货 */
    public static final String TYPE_REPLENISH = "REPLENISH";

    /** 建议类型:定价 */
    public static final String TYPE_PRICING = "PRICING";

    /** 建议类型:异常 */
    public static final String TYPE_ANOMALY = "ANOMALY";

    /** 建议类型:文案 */
    public static final String TYPE_COPYWRITING = "COPYWRITING";

    /** 关联业务类型:订单(异常建议 refType) */
    public static final String REF_TYPE_SHOP_ORDER = "SHOP_ORDER";

    /** 关联业务类型:库存(补货建议 refType,无 refId,dedup 键为 skuId) */
    public static final String REF_TYPE_INVENTORY = "INVENTORY";

    /** 风险等级:低 */
    public static final String RISK_LOW = "LOW";

    /** 风险等级:中 */
    public static final String RISK_MID = "MID";

    /** 风险等级:高(须人工复核) */
    public static final String RISK_HIGH = "HIGH";

    /** 会话消息角色:用户提问 */
    public static final String ROLE_USER = "USER";

    /** 会话消息角色:AI 回复 */
    public static final String ROLE_AI = "AI";

    /** 会话消息角色:工具调用(中间过程,当前链路取不到不落,词表预留) */
    public static final String ROLE_TOOL = "TOOL";

    /** 会话默认标题(首条消息后由 renameIfDefault 回填为摘要) */
    public static final String DEFAULT_SESSION_TITLE = "新会话";

    /** 会话来源:智能对话(chat 域) */
    public static final String SESSION_SOURCE_CHAT = "CHAT";

    /** 会话来源:智能体(四期 agent/) */
    public static final String SESSION_SOURCE_AGENT = "AGENT";

    /** 会话标题截断长度(首条消息截断作 title,DB 列 VARCHAR(128) 内) */
    public static final int TITLE_MAX_LEN = 24;

    private AiConsts() {
    }
}
