package com.own.erp.order.request.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 订单审核动作写侧入参(#29 订单域补课):只带裁定结果与备注——审核人/审核时间
 *     服务端回填(CurrentUserApi / 库 NOW()),review_status 目标值由 approve 派生(2/3),
 *     均不进客户端入参(docs/07 §1 服务端管理列)
 */
@Builder
public record ShopOrderReviewCommand(

        /** 裁定结果:true=通过(review_status=2) / false=驳回(review_status=3) */
        @NotNull
        Boolean approve,

        /** 审核/风控备注(可空,落 review_remark) */
        @Size(max = 500)
        String remark
) {
}
