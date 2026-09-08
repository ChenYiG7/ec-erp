package com.own.erp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 数据字典(sys_dict):一类一行,按 dictType 分组(物流公司、售后原因、国家编码...)。
 *     参考:启航 qihang-erp 的字典用法
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_dict")
public class SysDict {

    /** 新增组:仅 create 端点生效(update 保持 null-skip 部分更新语义,必填校验不进 Default 组) */
    public interface Create {
    }

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典类型,如 logistics_company / refund_reason */
    @NotBlank(groups = Create.class, message = "字典类型不能为空")
    @Size(max = 64, message = "字典类型不能超过 64 字")
    private String dictType;

    /** 显示名 */
    @NotBlank(groups = Create.class, message = "显示名不能为空")
    @Size(max = 128, message = "显示名不能超过 128 字")
    private String dictLabel;

    /** 存储值 */
    @NotBlank(groups = Create.class, message = "存储值不能为空")
    @Size(max = 128, message = "存储值不能超过 128 字")
    private String dictValue;

    /** 同级排序,小在前 */
    private Integer sort;

    /** 1=启用 0=禁用 */
    private Integer status;

    /** 备注 */
    @Size(max = 255, message = "备注不能超过 255 字")
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除:0=正常,非0=已删(值=被删行id);delval=id 配合唯一键含 deleted,删后同键可重建(TODO#7) */
    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
