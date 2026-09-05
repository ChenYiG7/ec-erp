package com.own.erp.aftersale.request.command;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 售后单人工处理动作入参(#12 状态机:agree/reject/receive-return/refund/complete 五动作共用)
 *     result = 处理结果回填;reject(拒绝)必填,其余动作可选,校验收口 Service(docs/07 §1 CQRS command 分包)
 */
@Builder
public record AftersaleHandleRequest(

        /** 处理结果(拒绝必填,如"质量不合格拒退";其余动作选填) */
        String result
) {
}
