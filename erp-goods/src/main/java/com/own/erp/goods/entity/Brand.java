package com.own.erp.goods.entity;

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
 * @Description : 品牌(brand)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("brand")
public class Brand {

    /** 新增组:仅 create 端点生效(update 保持 null-skip 部分更新语义,必填校验不进 Default 组) */
    public interface Create {
    }

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 品牌名称 */
    @NotBlank(groups = Create.class, message = "品牌名称不能为空")
    @Size(max = 128, message = "品牌名称不能超过 128 字")
    private String name;

    /** 品牌LOGO地址 */
    @Size(max = 512, message = "LOGO地址不能超过 512 字")
    private String logoUrl;

    /** 备注 */
    @Size(max = 255, message = "备注不能超过 255 字")
    private String remark;

    /** 1=启用 0=禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除:0=正常,非0=已删(值=被删行id);delval=id 配合唯一键含 deleted,删后同键可重建(TODO#7) */
    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
