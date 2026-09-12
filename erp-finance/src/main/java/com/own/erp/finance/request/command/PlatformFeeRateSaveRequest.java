package com.own.erp.finance.request.command;

import com.own.erp.finance.entity.PlatformFeeRate;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 平台费率表写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)
 *     record+@Builder(模型可变性分级 docs/07 §1);toEntity 用 entity builder 链一次成型(纯构造位,docs/07 §1 分级①)
 *     生成器已剔除 id/created_at/updated_at/deleted(服务端管理列);source 同为服务端管理列(V1 写侧固定 MANUAL);
 *     marketplace/category_path 为本期预留维度(估费 V1 只取全站点/全类目行,写侧不开放,启用随 #32 校差拍板)
 *
 * @param platform   平台(PlatformType 枚举名),必填
 * @param feeType    费种,V1 仅允许 COMMISSION(FBA 仓储类无费率不猜,Service 白名单校验)
 * @param rate       费率,0&lt;rate&lt;1(如 0.15=15%)
 * @param effFrom    生效起(含),必填
 * @param effTo      生效止(含),空=长期;不得早于 effFrom(Service 跨字段校验)
 * @param remark     备注
 */
@Builder
public record PlatformFeeRateSaveRequest(

        @NotBlank(message = "平台必填")
        @Size(max = 32)
        String platform,

        @NotBlank(message = "费种必填")
        @Size(max = 32)
        String feeType,

        @NotNull(message = "费率必填")
        @DecimalMin(value = "0", inclusive = false, message = "费率必须大于0")
        @DecimalMax(value = "1", inclusive = false, message = "费率必须小于1")
        BigDecimal rate,

        @NotNull(message = "生效起必填")
        LocalDate effFrom,

        LocalDate effTo,

        @Size(max = 255)
        String remark

) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);entity builder 链命名传参(与 from 同风格,防错位) */
    public PlatformFeeRate toEntity() {
        return PlatformFeeRate.builder()
                .platform(platform)
                .feeType(feeType)
                .rate(rate)
                .effFrom(effFrom)
                .effTo(effTo)
                .remark(remark)
                .build();
    }
}
