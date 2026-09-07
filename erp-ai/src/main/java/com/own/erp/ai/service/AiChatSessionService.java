package com.own.erp.ai.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiChatSession;
import com.own.erp.ai.mapper.AiChatSessionMapper;
import com.own.erp.ai.request.query.AiChatSessionQuery;
import com.own.erp.ai.response.AiChatSessionResponse;
import com.own.erp.common.exception.BusinessException;
import org.springframework.stereotype.Service;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI会话服务:ai_chat_session 域整域收口,Controller 不直连 Mapper(docs/07 §2.1)。
 *     会话写侧两个入口:①create 新建(默认标题,首条消息后 renameIfDefault 回填摘要);
 *     ②renameIfDefault 标题回填。历史读侧强制本人归属(个人助手口径仅本人可见,docs/03 §7)——
 *     越权与不存在统一"会话不存在",不泄露他人会话存在性
 */
@Service
public class AiChatSessionService {

    private final AiChatSessionMapper aiChatSessionMapper;

    public AiChatSessionService(AiChatSessionMapper aiChatSessionMapper) {
        this.aiChatSessionMapper = aiChatSessionMapper;
    }

    /** 我的会话分页(归属服务端强制覆盖入参,防越权查他人会话;按最近更新倒序,对齐 idx_user_updated;
     *  source 过滤隔离 chat/agent 两域会话列表,传 null 不过滤) */
    public Page<AiChatSessionResponse> pageMine(Long userId, AiChatSessionQuery query, String source) {
        query.setUserId(userId);
        LambdaQueryWrapper<AiChatSession> wrapper = new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getUserId, query.getUserId())
                .eq(StrUtil.isNotBlank(source), AiChatSession::getSource, source)
                .orderByDesc(AiChatSession::getUpdatedAt)
                .orderByDesc(AiChatSession::getId);
        Page<AiChatSession> result = aiChatSessionMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()), wrapper);
        Page<AiChatSessionResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(AiChatSessionResponse::from).toList());
        return responsePage;
    }

    /** 新建会话(title 空白落默认标题;updated_at 列 ON UPDATE CURRENT_TIMESTAMP 兜底随消息刷新) */
    public Long create(Long userId, String title) {
        return create(userId, title, AiConsts.SESSION_SOURCE_CHAT);
    }

    /** 新建会话(带来源:CHAT 智能对话 / AGENT 智能体,词表收口 AiConsts) */
    public Long create(Long userId, String title, String source) {
        AiChatSession session = AiChatSession.builder()
                .userId(userId)
                .title(StrUtil.isBlank(title) ? AiConsts.DEFAULT_SESSION_TITLE
                        : truncate(StrUtil.trim(title)))
                .source(source)
                .build();
        aiChatSessionMapper.insert(session);
        return session.getId();
    }

    /**
     * 归属校验取行(聊天/查历史前必经):不存在或非本人一律抛"会话不存在"——
     * 统一口径避免探测他人会话ID的存在性;source 跨源强约束(chat/agent 两域会话互不可见,
     * 不符合同报"会话不存在",不泄露跨域会话存在性,2026-09-07 拍板收口)
     */
    public AiChatSession getOwned(Long sessionId, Long userId, String source) {
        AiChatSession session = aiChatSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)
                || !source.equals(session.getSource())) {
            throw new BusinessException("会话不存在");
        }
        return session;
    }

    /** 首条消息回填标题:仅当仍是默认标题时截断回填;非默认(建会话时已命名)不动 */
    public void renameIfDefault(Long sessionId, String title) {
        AiChatSession session = aiChatSessionMapper.selectById(sessionId);
        if (session == null || !AiConsts.DEFAULT_SESSION_TITLE.equals(session.getTitle())) {
            return;
        }
        AiChatSession update = new AiChatSession();
        update.setId(sessionId);
        update.setTitle(truncate(title));
        aiChatSessionMapper.updateById(update);
    }

    /** 标题截断(_ai_chat_session.title VARCHAR(128),取可读摘要长度,防超长报错) */
    private static String truncate(String title) {
        String trimmed = StrUtil.trim(title);
        if (StrUtil.isBlank(trimmed)) {
            return AiConsts.DEFAULT_SESSION_TITLE;
        }
        return trimmed.length() <= AiConsts.TITLE_MAX_LEN ? trimmed
                : trimmed.substring(0, AiConsts.TITLE_MAX_LEN);
    }
}
