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
 * @Description : 商品 SPU(product):内部商品库主档
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 内部 SPU 编码,唯一 */
    private String spuCode;

    /** 商品名称 */
    private String name;

    /** 分类ID(product_category.id) */
    private Long categoryId;

    /** 品牌ID(brand.id) */
    private Long brandId;

    /** 销售属性(JSON),如 [{"name":"颜色","values":["黑","白"]}] */
    private String attrsJson;

    /** 1=在售 0=停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
