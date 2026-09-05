package com.own.erp.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.system.auth.AuthContext;
import com.own.erp.system.request.query.SysNotificationQuery;
import com.own.erp.system.response.SysNotificationResponse;
import com.own.erp.system.service.SysNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 站内通知(#14):SysNotification 域整域收口,一律走 SysNotificationService(docs/07 §2.1)。
 *     系统写入表:告警写入口在 Service(pushAllUsers 扇出),对外仅只读查询 + 本人已读状态变更
 */
@Tag(name = "站内通知", description = "站内通知(系统告警,只读+本人已读状态)")
@RestController
@RequestMapping("/api/system/notifications")
@RequiredArgsConstructor
public class SysNotificationController {

    private final SysNotificationService sysNotificationService;

    @Operation(summary = "我的通知分页", description = "过滤参数:readStatus(0未读/1已读)、notifyType")
    @GetMapping
    public Result<Page<SysNotificationResponse>> page(SysNotificationQuery query) {
        return Result.ok(sysNotificationService.page(AuthContext.current().userId(), query));
    }

    @Operation(summary = "我的通知详情", description = "不存在或非本人通知返回 null data")
    @GetMapping("/{id}")
    public Result<SysNotificationResponse> get(@PathVariable Long id) {
        return Result.ok(sysNotificationService.getOwned(AuthContext.current().userId(), id));
    }

    @Operation(summary = "未读数", description = "前端红点")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(sysNotificationService.unreadCount(AuthContext.current().userId()));
    }

    @Operation(summary = "标记单条已读", description = "非本人/不存在/已读报业务错误")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        sysNotificationService.markRead(AuthContext.current().userId(), id);
        return Result.ok();
    }

    @Operation(summary = "全部标记已读", description = "返回本次标记条数,无未读返回 0")
    @PutMapping("/read-all")
    public Result<Integer> markAllRead() {
        return Result.ok(sysNotificationService.markAllRead(AuthContext.current().userId()));
    }
}
