package com.own.erp.ai.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI会话分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 *     userId 为服务端强制覆盖字段(个人助手口径仅本人可见,前端传入被忽略)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatSessionQuery extends PageQuery {

    /** 用户ID(sys_user.id,服务端按当前登录人强制覆盖,防越权查他人会话) */
    private Long userId;
}
