package com.own.erp.ai.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.mapper.AiSuggestionMapper;
import com.own.erp.ai.request.query.AiSuggestionQuery;
import com.own.erp.ai.response.AiSuggestionResponse;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI建议服务:ai_suggestion 域整域收口,Controller 不直连 Mapper(docs/07 §2.1)。
 *     系统写入表:写侧两条路径——①AI 产出 save(内部唯一入口,系统链路,无登录态);
 *     ②人工确认 adopt/ignore(cas 条件更新即守卫,0待确认→1已采纳/2已忽略,同 UPDATE 回填确认人/时间)。
 *     AI 永不直接写业务表:人工采纳后由前端引导走对应业务接口(docs/07 §7)
 */
@Service
public class AiSuggestionService {

    private final AiSuggestionMapper aiSuggestionMapper;
    private final CurrentUserApi currentUserApi;

    /** 契约接口注入一律 @Lazy 断构造环:实现收口 erp-api 反向注入域 Service(docs/07 §2.2) */
    public AiSuggestionService(AiSuggestionMapper aiSuggestionMapper, @Lazy CurrentUserApi currentUserApi) {
        this.aiSuggestionMapper = aiSuggestionMapper;
        this.currentUserApi = currentUserApi;
    }

    /** 分页查询(过滤:店铺/SKU/类型/确认状态;按 id 倒序) */
    public Page<AiSuggestionResponse> page(AiSuggestionQuery query) {
        LambdaQueryWrapper<AiSuggestion> wrapper = new LambdaQueryWrapper<AiSuggestion>()
                .eq(query.getShopId() != null, AiSuggestion::getShopId, query.getShopId())
                .eq(query.getSkuId() != null, AiSuggestion::getSkuId, query.getSkuId())
                .eq(StrUtil.isNotBlank(query.getSuggestionType()), AiSuggestion::getSuggestionType, query.getSuggestionType())
                .eq(query.getStatus() != null, AiSuggestion::getStatus, query.getStatus())
                .orderByDesc(AiSuggestion::getId);
        Page<AiSuggestion> result = aiSuggestionMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()), wrapper);
        Page<AiSuggestionResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(AiSuggestionResponse::from).toList());
        return responsePage;
    }

    /** 详情,出参 Response;不存在返回 null */
    public AiSuggestionResponse getById(Long id) {
        AiSuggestion aiSuggestion = aiSuggestionMapper.selectById(id);
        return aiSuggestion == null ? null : AiSuggestionResponse.from(aiSuggestion);
    }

    /**
     * 采纳建议(人工确认闭环):cas 0待确认→1已采纳,同 UPDATE 回填确认人/确认时间;
     * affected=0 即不存在或已被处理(终态互斥),拒绝;确认人走当前登录用户(HTTP 链路限定)
     */
    public void adopt(Long id) {
        int affected = aiSuggestionMapper.casAdopt(id, currentUserApi.currentUserId());
        if (affected == 0) {
            throw new BusinessException("建议不存在或已处理,采纳失败");
        }
    }

    /** 忽略建议(人工确认闭环):cas 0待确认→2已忽略,同 adopt 守卫口径 */
    public void ignore(Long id) {
        int affected = aiSuggestionMapper.casIgnore(id, currentUserApi.currentUserId());
        if (affected == 0) {
            throw new BusinessException("建议不存在或已处理,忽略失败");
        }
    }

    /**
     * AI 产出落库唯一入口(系统链路调用——AI Job/工作流,无登录态不经 CurrentUserApi;禁旁路 insert):
     * 类型与摘要必填(LLM 结论一句话必回传,summary 列 NOT NULL 前置校验);
     * 状态/风险词表校验在产出侧由 AiConsts 约束,此处只兜底必填;幂等/去重由调用方约束(建议无业务唯一键)
     */
    public Long save(AiSuggestion suggestion) {
        if (StrUtil.isBlank(suggestion.getSuggestionType()) || StrUtil.isBlank(suggestion.getSummary())) {
            throw new BusinessException("建议类型与摘要必填,AI 产出落库失败");
        }
        aiSuggestionMapper.insert(suggestion);
        return suggestion.getId();
    }

    /**
     * 定时接线去重读侧(2026-09-07 拍板:同键存在待确认(status=0)建议即跳过——旧建议被采纳/忽略后
     * 若单据仍命中允许再产出,确认闭环自然运转):返回该类型+refType 全部待确认行的关联单据ID。
     * eq 条件查全量 + 内存取值,规避 Wrapper.in() 急切解析坑(docs/07 §10);待确认量级 = 人工未处理积压,天然有界
     */
    public Set<Long> findPendingRefIds(String suggestionType, String refType) {
        return aiSuggestionMapper.selectList(new LambdaQueryWrapper<AiSuggestion>()
                        .eq(AiSuggestion::getSuggestionType, suggestionType)
                        .eq(AiSuggestion::getRefType, refType)
                        .eq(AiSuggestion::getStatus, AiConsts.STATUS_PENDING))
                .stream()
                .map(AiSuggestion::getRefId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** 同 findPendingRefIds,按 skuId 维度去重(补货建议 refType=INVENTORY 无 refId) */
    public Set<Long> findPendingSkuIds(String suggestionType) {
        return aiSuggestionMapper.selectList(new LambdaQueryWrapper<AiSuggestion>()
                        .eq(AiSuggestion::getSuggestionType, suggestionType)
                        .eq(AiSuggestion::getStatus, AiConsts.STATUS_PENDING))
                .stream()
                .map(AiSuggestion::getSkuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
