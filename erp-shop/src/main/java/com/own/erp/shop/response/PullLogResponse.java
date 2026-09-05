package com.own.erp.shop.response;

import com.own.erp.shop.entity.PullLog;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 拉取日志对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进 Response 即编译期封死
 */
@Builder
public record PullLogResponse(

        /** 主键 */
        Long id,

        /** 店铺ID(shop.id) */
        Long shopId,

        /** ORDER/PRODUCT/REFUND */
        String dataType,

        /** 拉取窗口起点(含) */
        LocalDateTime windowStart,

        /** 拉取窗口终点(含);游标=最近成功记录的window_end */
        LocalDateTime windowEnd,

        /** 本次拉取条数 */
        Integer pulledCount,

        /** 1成功0失败 */
        Integer success,

        /** 失败原因 */
        String errorMsg,

        /** 本次拉取耗时(毫秒),观测慢店铺/慢接口 */
        Integer durationMs,

        /** 触发方式:JOB定时/MANUAL手动 */
        String pullWay,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static PullLogResponse from(PullLog entity) {
        return PullLogResponse.builder()
                .id(entity.getId())
                .shopId(entity.getShopId())
                .dataType(entity.getDataType())
                .windowStart(entity.getWindowStart())
                .windowEnd(entity.getWindowEnd())
                .pulledCount(entity.getPulledCount())
                .success(entity.getSuccess())
                .errorMsg(entity.getErrorMsg())
                .durationMs(entity.getDurationMs())
                .pullWay(entity.getPullWay())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
