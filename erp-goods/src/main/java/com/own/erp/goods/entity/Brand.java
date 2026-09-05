package com.own.erp.goods.entity;

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
 * @Description : 品牌(brand)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("brand")
public class Brand {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 品牌名称 */
    private String name;

    /** 品牌LOGO地址 */
    private String logoUrl;

    /** 备注 */
    private String remark;

    /** 1=启用 0=禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
