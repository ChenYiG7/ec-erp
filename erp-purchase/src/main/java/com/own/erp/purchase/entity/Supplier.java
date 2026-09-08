package com.own.erp.purchase.entity;

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
 * @Description : 供应商(supplier)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("supplier")
public class Supplier {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 供应商名称 */
    private String name;

    /** 联系人 */
    private String contact;

    /** 联系电话 */
    private String phone;

    /** 结算方式,走 sys_dict(预付/月结等) */
    private String settleType;

    /** 备注 */
    private String remark;

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
