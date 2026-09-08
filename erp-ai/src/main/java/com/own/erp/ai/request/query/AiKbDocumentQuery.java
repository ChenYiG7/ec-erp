package com.own.erp.ai.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 知识库文档分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbDocumentQuery extends PageQuery {

    /** 标题模糊过滤 */
    private String title;

    /** 状态过滤:READY/FAILED(词表 AiConsts.KB_STATUS_*) */
    private String status;
}
