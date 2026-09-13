package com.own.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 调拨单(仓内作业,V1 确认即达;两腿经 InventoryService.transfer 同事务写 flow)(transfer_order)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("transfer_order")
public class TransferOrder {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调拨单号 TR+yyyyMMdd+seq,唯一 */
    private String transferNo;

    /** 调出仓ID(warehouse.id) */
    private Long fromWarehouseId;

    /** 调入仓ID(warehouse.id) */
    private Long toWarehouseId;

    /** DRAFT草稿/IN_TRANSIT在途(已发未达)/CONFIRMED已确认(调拨已达)/CANCELED已取消 */
    private String status;

    /** 动账模式(#30 余量①):DIRECT确认即达(V1默认)/IN_TRANSIT在途(OUT→到货IN) */
    private String transitMode;

    /** 备注 */
    private String remark;

    /** 创建人(sys_user.id) */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
