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
 * @Description : 商品分类(product_category):parentId 自关联成树
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("product_category")
public class ProductCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父分类ID,根节点为 0 */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 同级排序,小在前 */
    private Integer sort;

    /** 1=启用 0=禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
