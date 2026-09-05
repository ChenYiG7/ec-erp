package com.own.erp.shop.entity;

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
 * @Description : 平台拉取日志(增量游标依据:取成功记录的window_end,左叠5分钟)(pull_log)
 */
@Data
@TableName("pull_log")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullLog {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺ID(shop.id) */
    private Long shopId;

    /** ORDER/PRODUCT/REFUND */
    private String dataType;

    /** 拉取窗口起点(含) */
    private LocalDateTime windowStart;

    /** 拉取窗口终点(含);游标=最近成功记录的window_end */
    private LocalDateTime windowEnd;

    /** 本次拉取条数 */
    private Integer pulledCount;

    /** 1成功0失败 */
    private Integer success;

    /** 失败原因 */
    private String errorMsg;

    /** 本次拉取耗时(毫秒),观测慢店铺/慢接口 */
    private Integer durationMs;

    /** 触发方式:JOB定时/MANUAL手动 */
    private String pullWay;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
