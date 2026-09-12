package com.own.erp.system.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 操作审计分页查询入参(#27②;docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 *     时间窗 ISO 格式(2026-09-12T00:00:00),@DateTimeFormat 绑定 LocalDateTime
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysOperLogQuery extends PageQuery {

    /** 操作人登录名,模糊匹配,空=全部 */
    private String username;

    /** 业务模块精确过滤:order/inventory/fulfill/purchase/finance,空=全部 */
    private String module;

    /** 结果过滤:OK/FAIL,空=全部 */
    private String resultStatus;

    /** 操作时间起(含) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime beginTime;

    /** 操作时间止(含) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
