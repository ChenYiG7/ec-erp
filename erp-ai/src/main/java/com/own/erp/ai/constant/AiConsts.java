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

    /** 建议类型:采购(#17 三期候选落地,2026-09-08:补货缺口按供应商聚合为采购计划建议) */
    public static final String TYPE_PURCHASE = "PURCHASE";

    /** 建议类型:选品(#17 落位表「智能选品」三期提前,2026-09-08:启用 SKU 三维加权评分产重点关注建议) */
    public static final String TYPE_SELECTION = "SELECTION";

    /** 关联业务类型:订单(异常建议 refType) */
    public static final String REF_TYPE_SHOP_ORDER = "SHOP_ORDER";

    /** 关联业务类型:库存(补货建议 refType,无 refId,dedup 键为 skuId) */
    public static final String REF_TYPE_INVENTORY = "INVENTORY";

    /** 关联业务类型:供应商(采购建议 refType,refId=supplierId,dedup 键为 refId) */
    public static final String REF_TYPE_SUPPLIER = "SUPPLIER";

    /** 关联业务类型:商品(文案建议 refType,#17 产品描述生成 2026-09-08 加,refId=productId,dedup 键为 refId) */
    public static final String REF_TYPE_GOODS_PRODUCT = "GOODS_PRODUCT";

    /** 关联业务类型:商品SKU(选品建议 refType,#17 智能选品 2026-09-08 加,refId=skuId,dedup 键为 refId) */
    public static final String REF_TYPE_GOODS_SKU = "GOODS_SKU";

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

    /** 知识库文档来源:文件上传(.txt/.md) */
    public static final String KB_SOURCE_UPLOAD = "UPLOAD";

    /** 知识库文档来源:粘贴文本 */
    public static final String KB_SOURCE_TEXT = "TEXT";

    /** 知识库文档状态:READY(分块已入库且向量化成功,参与检索) */
    public static final String KB_STATUS_READY = "READY";

    /** 知识库文档状态:FAILED(文本与分块已留存,向量化失败——修复后可重建索引转 READY) */
    public static final String KB_STATUS_FAILED = "FAILED";

    /** 向量库文档元数据键:所属知识库文档ID(ai_kb_document.id,按文档清理向量用) */
    public static final String KB_META_DOCUMENT_ID = "documentId";

    /** 向量库文档元数据键:文档标题(检索命中回显来源) */
    public static final String KB_META_TITLE = "title";

    private AiConsts() {
    }
}
