package com.own.erp.system.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 站内通知分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 *     过滤条件字段按业务在此补(pageNo/pageSize 已由 PageQuery 提供并钳制 ≤500)
 */
/**
 * 保持 class(模型可变性分级 docs/07 §1 的继承例外):record 不能继承类,
 * 而 XxxQuery 必须继承 PageQuery 的钳制分页——可变性豁免,后续 PageQuery 重构再议
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysNotificationQuery extends PageQuery {

    /** 已读状态过滤:0未读 1已读,空=全部 */
    private Integer readStatus;

    /** 通知类型过滤,如 PULL_FAIL,空=全部 */
    private String notifyType;
}
