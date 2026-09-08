package com.own.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 仓库(warehouse)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("warehouse")
public class Warehouse {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 仓库名称 */
    private String whName;

    /** SELF自仓/FBA/OVERSEAS海外仓/VIRTUAL虚拟仓 */
    private String whType;

    /** 国家(ISO 3166) */
    private String country;

    /** 仓库地址 */
    private String address;

    /** 1启用0禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除:0=正常,非0=已删(值=被删行id);delval=id 配合唯一键含 deleted,删后同键可重建(TODO#7) */
    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
