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
 * @Date : 2026/9/7
 * @Description : 系统参数(启动后可变项键值对:大模型/预警阈值/AI工作流参数等)(sys_config)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_config")
public class SysConfig {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 参数组:AI=大模型与AI工作流(含对话/Agent提示词与连接) ALERT=库存预警 SALES=销量统计 */
    private String configGroup;

    /** 参数键(与 yml relaxed-binding 键同名,如 erp.ai.replenish.low-stock-threshold),唯一 */
    private String configKey;

    /** 参数值(文本存储,数字/布尔由消费侧解析;凭证类禁入本表) */
    private String configValue;

    /** 参数说明(前端表单旁展示) */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
