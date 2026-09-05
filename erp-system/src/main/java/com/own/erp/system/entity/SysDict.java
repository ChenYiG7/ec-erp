package com.own.erp.system.entity;

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

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典类型,如 logistics_company / refund_reason */
    private String dictType;

    /** 显示名 */
    private String dictLabel;

    /** 存储值 */
    private String dictValue;

    /** 同级排序,小在前 */
    private Integer sort;

    /** 1=启用 0=禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
