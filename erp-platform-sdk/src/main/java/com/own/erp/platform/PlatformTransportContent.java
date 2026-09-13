package com.own.erp.platform;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA 板箱运输信息回传命令(#35 fba-shipment V2,防腐层平台中立模型):
 *         putTransportContent 要素,预写小包裹主形态(合作承运 PartneredSmallParcelData /
 *         非合作 NonPartneredSmallParcelData 两态);联调时按真实下单方式校准字段(docs/07 §8)。
 *         partnered=true 时 contact + boxes 必填;false 时 carrierName 必填(校验归客户端入参防御)
 */
@Builder
public record PlatformTransportContent(
        /** 是否亚马逊合作承运(Shipment Service 拍板归联调确认) */
        boolean partnered,
        /** 联系人(partnered 必填) */
        String contactName,
        /** 联系电话(partnered 必填) */
        String contactPhone,
        /** 板箱清单(partnered 必填,箱数 = 列表长度) */
        List<Box> boxes,
        /** 承运商名称(非合作小包裹必填) */
        String carrierName) {

    @Builder
    public record Box(
            /** 箱长 */
            BigDecimal length,
            /** 箱宽 */
            BigDecimal width,
            /** 箱高 */
            BigDecimal height,
            /** 尺寸单位(in/cm) */
            String dimensionUnit,
            /** 箱重 */
            BigDecimal weight,
            /** 重量单位(lb/kg) */
            String weightUnit) {
    }
}
