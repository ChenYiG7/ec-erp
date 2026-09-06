package com.own.erp.system.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.entity.SysNotification;
import com.own.erp.system.mapper.SysNotificationMapper;
import com.own.erp.system.request.query.SysNotificationQuery;
import com.own.erp.system.response.SysNotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 站内通知服务(#14):sys_notification 域整域收口,Controller 不直连 Mapper(docs/07 §2.1)。
 *     系统写入表:写侧唯一入口 pushAllUsers(系统告警扇出,禁旁路 insert);用户侧仅已读状态变更(本人归属校验),
 *     无人工 CRUD 写接口;通知类型/业务类型常量收口本类,拉单告警接线见 OrderPullJob/ProductPullJob(#4/#5)
 *     后续邮件/短信/IM 渠道(微信/飞书/钉钉/企微,2026-09-04 拍板后续做)从 pushAllUsers 出口处扩展,本期不提前抽象
 */
@Service
@RequiredArgsConstructor
public class SysNotificationService {

    /** 通知类型:拉单连续失败告警 */
    public static final String TYPE_PULL_FAIL = "PULL_FAIL";
    /** 关联业务类型:店铺 */
    public static final String BIZ_TYPE_SHOP = "SHOP";

    private static final int READ_UNREAD = 0;
    private static final int READ_READ = 1;
    /** content 列 VARCHAR(1024),写侧截断留余量 */
    private static final int CONTENT_MAX_LENGTH = 1000;

    private final SysNotificationMapper sysNotificationMapper;
    private final SysUserService sysUserService;

    /**
     * 系统告警唯一写入口:扇出到全部启用用户(V1 全员广播,当前用户量级小逐条直插;
     * 后续邮件/短信/IM 渠道在此出口处扩展)。返回写入条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int pushAllUsers(String notifyType, String title, String content, String bizType, Long bizId) {
        List<Long> userIds = sysUserService.listEnabledUserIds();
        if (CollUtil.isEmpty(userIds)) {
            return 0;
        }
        for (Long userId : userIds) {
            sysNotificationMapper.insert(build(userId, notifyType, title, content, bizType, bizId));
        }
        return userIds.size();
    }

    /**
     * 近期是否已发过同类型告警(#6 预警引擎静默期去重):sys_notification 自身即"上次告警时间"存储,
     * 免建去重表;走 idx_created 范围条件,告警量级(每日个位数)足够。仅查系统侧扇出行,不限用户
     */
    public boolean existsRecent(String notifyType, LocalDateTime since) {
        return sysNotificationMapper.selectCount(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getNotifyType, notifyType)
                .gt(SysNotification::getCreatedAt, since)) > 0;
    }

    /** 我的通知分页(归属固定当前用户;过滤:已读状态/通知类型) */
    public Page<SysNotificationResponse> page(Long userId, SysNotificationQuery query) {
        Page<SysNotification> result = sysNotificationMapper.selectPage(
                new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getUserId, userId)
                        .eq(query.getReadStatus() != null, SysNotification::getReadStatus, query.getReadStatus())
                        .eq(StrUtil.isNotBlank(query.getNotifyType()), SysNotification::getNotifyType, query.getNotifyType())
                        .orderByDesc(SysNotification::getId));
        Page<SysNotificationResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(SysNotificationResponse::from).toList());
        return responsePage;
    }

    /** 我的通知详情;不存在或不属于本人返回 null(不暴露他人通知) */
    public SysNotificationResponse getOwned(Long userId, Long id) {
        SysNotification notification = sysNotificationMapper.selectById(id);
        return notification == null || !userId.equals(notification.getUserId())
                ? null
                : SysNotificationResponse.from(notification);
    }

    /** 未读数(前端红点) */
    public Long unreadCount(Long userId) {
        return sysNotificationMapper.selectCount(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getReadStatus, READ_UNREAD));
    }

    /** 标记单条已读(归属校验:非本人/不存在/已读视同失败,不静默);条件用 eq(惰性解析),set 值走实体 null-skip */
    public void markRead(Long userId, Long id) {
        int rows = sysNotificationMapper.update(readUpdate(), new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getId, id)
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getReadStatus, READ_UNREAD));
        if (rows == 0) {
            throw new BusinessException("通知不存在或已是已读状态");
        }
    }

    /** 全部标记已读,返回本次标记条数(无未读返回 0,不报错) */
    public int markAllRead(Long userId) {
        return sysNotificationMapper.update(readUpdate(), new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getReadStatus, READ_UNREAD));
    }

    /** 已读状态更新值(写侧 entity 装配白名单;LambdaUpdateWrapper.set 会急切解析列名,纯单测环境无 MP 元数据,故不用) */
    private SysNotification readUpdate() {
        SysNotification update = new SysNotification();
        update.setReadStatus(READ_READ);
        update.setReadAt(LocalDateTime.now(PullConsts.ZONE));
        return update;
    }

    /** 行装配统一走 builder(docs/07 §1 模型可变性分级):一次成型,无中间可变态 */
    private SysNotification build(Long userId, String notifyType, String title, String content, String bizType, Long bizId) {
        return SysNotification.builder()
                .userId(userId)
                .title(StrUtil.maxLength(title, CONTENT_MAX_LENGTH))
                .content(StrUtil.isBlank(content) ? null : StrUtil.maxLength(content, CONTENT_MAX_LENGTH))
                .notifyType(notifyType)
                .bizType(bizType)
                .bizId(bizId)
                .readStatus(READ_UNREAD)
                .build();
    }
}
