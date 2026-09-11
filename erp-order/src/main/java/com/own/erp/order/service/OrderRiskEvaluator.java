package com.own.erp.order.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.own.erp.common.constant.ConfigConsts;
import com.own.erp.contract.SystemConfigApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 订单审核风控判定器(#29 订单域补课,计划书 §2.1 拍板①:不做"全部 WAIT_SHIP 全量待审",
 *     改「风险规则命中才置待审核,否则直过」;V1 仅两条规则,后续扩容只加本类内部分支):
 *     ①地址不完整——收货六列(姓名/电话/国家/城市/详细地址/邮编)任一空白,或 zip 明显非法
 *       (非字母数字/连字符/空格,或长度 <3/&gt;12,典型脏值:中文、含"未知"、纯符号);
 *     ②买家留言命中风控关键词——关键词表读 sys_config(键 erp.order.review.risk-keywords,英文逗号分隔,
 *       经 SystemConfigApi 短 TTL 缓存 + 保存侧事件失效,热更后新订单秒级生效,#18 同构);
 *     判定结果落 shop_order.risk_flag(命中摘要,空=未命中)并决定 review_status 初值——
 *     ⚠️ 该列只在订单首次落库时写(upsert 的 ODKU 清单排除审核列),平台重拉不冲人工审核结论。
 *     判定是纯函数式只读动作(无副作用、无异常外抛),配置缺失/异常一律回落"不命中",不阻断拉单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRiskEvaluator {

    /** 邮编合法形态:字母数字开头,后可跟字母数字/连字符/空格,总长 3~12(覆盖 CN 6 位 / US 5-10 / 带连字符形态) */
    private static final Pattern ZIP_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9\\- ]{1,11}$");

    /** risk_flag 列 VARCHAR(255),摘要超长截断留余量 */
    private static final int RISK_FLAG_MAX_LENGTH = 240;

    private final @Lazy SystemConfigApi systemConfigApi;

    /**
     * 评估风控命中情况(纯只读)
     *
     * @return 命中摘要(多规则以 "; " 连接),未命中返回 null
     */
    public String evaluate(String buyerNote, String receiverName, String receiverPhone,
                           String receiverCountry, String receiverCity, String receiverAddress,
                           String receiverZip) {
        List<String> reasons = new ArrayList<>(2);
        List<String> missing = new ArrayList<>(6);
        addIfBlank(missing, receiverName, "收货人姓名");
        addIfBlank(missing, receiverPhone, "收货电话");
        addIfBlank(missing, receiverCountry, "收货国家");
        addIfBlank(missing, receiverCity, "收货城市");
        addIfBlank(missing, receiverAddress, "详细地址");
        addIfBlank(missing, receiverZip, "邮编");
        if (CollUtil.isNotEmpty(missing)) {
            reasons.add("地址不完整:" + String.join("/", missing));
        } else if (!ZIP_PATTERN.matcher(receiverZip.strip()).matches()) {
            reasons.add("邮编格式异常:" + receiverZip.strip());
        }
        List<String> keywords = hitKeywords(buyerNote);
        if (CollUtil.isNotEmpty(keywords)) {
            reasons.add("风控关键词:" + String.join("/", keywords));
        }
        if (reasons.isEmpty()) {
            return null;
        }
        return StrUtil.maxLength(String.join("; ", reasons), RISK_FLAG_MAX_LENGTH);
    }

    /** 关键词命中(大小写不敏感 contains);词表未配置/读取异常→空集(不命中,不阻断拉单) */
    private List<String> hitKeywords(String buyerNote) {
        if (StrUtil.isBlank(buyerNote)) {
            return List.of();
        }
        String configured = configValue();
        if (StrUtil.isBlank(configured)) {
            return List.of();
        }
        List<String> hits = new ArrayList<>();
        for (String keyword : configured.split(",")) {
            String trimmed = keyword.strip();
            if (!trimmed.isEmpty() && StrUtil.containsIgnoreCase(buyerNote, trimmed)) {
                hits.add(trimmed);
            }
        }
        return hits;
    }

    private String configValue() {
        try {
            return systemConfigApi.valueOf(ConfigConsts.KEY_ORDER_REVIEW_RISK_KEYWORDS);
        } catch (Exception e) {
            // 配置读取异常不阻断拉单:风控判定失败等价于"不命中",人工可由订单列表复核
            log.warn("风控关键词表读取失败,本次按未命中处理", e);
            return null;
        }
    }

    private static void addIfBlank(List<String> missing, String value, String label) {
        if (StrUtil.isBlank(value)) {
            missing.add(label);
        }
    }
}
