package com.own.erp.platform.adapter.amazon;

import cn.hutool.core.util.StrUtil;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.unified.UnifiedSettlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : Amazon 结算报告 V2 flat file(TSV)→ UnifiedSettlement 翻译器(#19,防腐层内平台差异唯一容身处):
 *         - 报表类型 **GET_V2_SETTLEMENT_REPORT_DATA_FLAT_FILE_V2**(V1 flat file/XML 官方 2026-11-11 移除,
 *           禁再引用);V2 金额收敛为三通用列 amount-type/amount-description/amount(官方字段名);
 *         - 文件三段结构:表头行 → 结算头行(settlement-id 列非空,带周期/deposit-date/total-amount/currency)
 *           → 事件行(transaction-type 列非空;settlement 列空)→ 空行 + "Settlement Total" 汇总段;
 *           **真实报告文件到位后须 --force 校准一轮**(docs/07 §8,现有 fixture 为官方文档结构推导的自制样例);
 *         - 金额:报告原值带符号存(正=收入/负=费用,禁取绝对值,勾稽=Σ明细 vs 报告头 total-amount);
 *           **本地化小数格式**:官方注明 EU 站点金额为 "95,00" 形态——EUR 按"点千分位逗号小数"解析,其余按 US 形态
 *           "逗号千分位点小数"(站点↔币种已收口 AmazonMarketplace 静态表);
 *         - 勾稽拍板:结算头 total-amount 为基准(V1 不依赖 footer 汇总段,形态容忍跳过随真凭证实测校准),
 *           Σ明细≠total 落库 FAILED 留痕可重拉,禁静默截断(拍板见 01_schema_init.sql settlement_report 注释);
 *         - fee_type 归一(UnifiedSettlement.FeeType,值集只加不改;⚠ 报文里佣金/FBA 费等费用行
 *           transaction-type=Order,描述与 amount-type 检查**必须先于 Order 短路**):
 *           Transfer→TRANSFER / Refund→REFUND(含退款负佣金,退款全额对勾稽)/ Commission 系→COMMISSION /
 *           FBA*→FBA_FEE、Storage Fee→STORAGE、ABA-*→ADVERTISING / Order→SALE(剩余订单侧收入流
 *           含运费税费促销)/ 其余→OTHER 兜底不丢数据;
 *         - 核心:结算头必填列缺失/金额缺失/记账时间缺失即抛异常禁静默(docs/07 §8),上游编排记 pull_log
 */
final class AmazonSettlementTranslator {

    private AmazonSettlementTranslator() {
    }

    /** TSV 全文 → UnifiedSettlement(单报告;事件行序即报告行序,稳定) */
    static UnifiedSettlement translate(String tsv, Long shopId, PlatformType platform) {
        if (StrUtil.isBlank(tsv)) {
            throw new IllegalStateException("结算报告内容为空,拒绝静默落库");
        }
        String[] lines = tsv.split("\r?\n");
        Map<String, Integer> columnIndex = columnIndex(lines[0]);

        UnifiedSettlement.UnifiedSettlementBuilder builder = UnifiedSettlement.builder()
                .shopId(shopId).platform(platform);
        List<UnifiedSettlement.Line> events = new ArrayList<>();
        String currency = null;
        boolean headerSeen = false;
        boolean footerMode = false;
        for (int i = 1; i < lines.length; i++) {
            if (StrUtil.isBlank(lines[i])) {
                footerMode = true;  // 空行后为官方汇总段(Settlement Total),V1 勾稽不依赖,容忍跳过(拍板见类注释)
                continue;
            }
            String[] cells = lines[i].split("\t", -1);
            String settlementId = cell(cells, columnIndex, "settlement-id");
            if (footerMode || settlementId.startsWith("Settlement Total")) {
                footerMode = true;
                continue;
            }
            String transactionType = cell(cells, columnIndex, "transaction-type");
            if (StrUtil.isNotBlank(settlementId)) {
                if (headerSeen) {
                    throw new IllegalStateException("结算报告第 " + (i + 1) + " 行出现第二个结算头,非法结构拒绝落库");
                }
                headerSeen = true;
                currency = required(cells, columnIndex, "currency", i);
                builder.settlementId(settlementId)
                        .periodStart(parseTime(required(cells, columnIndex, "settlement-start-date", i)))
                        .periodEnd(parseTime(required(cells, columnIndex, "settlement-end-date", i)))
                        .depositDate(parseTime(required(cells, columnIndex, "deposit-date", i)))
                        .currency(currency)
                        .totalAmount(parseAmount(required(cells, columnIndex, "total-amount", i), currency));
                continue;
            }
            if (StrUtil.isBlank(transactionType)) {
                continue; // 结算列与事件列全空的残行,无资产数据
            }
            String amountRaw = required(cells, columnIndex, "amount", i);
            String postedRaw = cell(cells, columnIndex, "posted-date-time");
            if (StrUtil.isBlank(postedRaw)) {
                postedRaw = required(cells, columnIndex, "posted-date", i);
            }
            events.add(UnifiedSettlement.Line.builder()
                    .orderId(StrUtil.emptyToNull(cell(cells, columnIndex, "order-id")))
                    .orderItemId(StrUtil.emptyToNull(cell(cells, columnIndex, "order-item-code")))
                    .sku(StrUtil.emptyToNull(cell(cells, columnIndex, "sku")))
                    .transactionType(transactionType)
                    .feeType(feeType(transactionType, cell(cells, columnIndex, "amount-type"),
                            cell(cells, columnIndex, "amount-description")))
                    .amount(parseAmount(amountRaw, currency))
                    .postedAt(parseTime(postedRaw))
                    .quantity(integer(cell(cells, columnIndex, "quantity-purchased")))
                    .build());
        }
        if (!headerSeen) {
            throw new IllegalStateException("结算报告缺结算头行(settlement-id 列恒空),非法结构拒绝落库");
        }
        return builder.lines(events).build();
    }

    /** 表头行 → 列名索引(缺失必填列即报错,带实际表头便于联调诊断模板差异) */
    private static Map<String, Integer> columnIndex(String headerLine) {
        String[] headers = headerLine.split("\t", -1);
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            index.putIfAbsent(headers[i].trim(), i);
        }
        for (String required : List.of("settlement-id", "total-amount", "currency",
                "transaction-type", "amount")) {
            if (!index.containsKey(required)) {
                throw new IllegalStateException("结算报告缺必填列 " + required + ",实际表头:" + headerLine);
            }
        }
        return index;
    }

    /**
     * fee_type 归一(映射规则版本化,只加不改):
     * 1.Trans→TRANSFER 2.Refund→REFUND(退款行含其负佣金,退款全额对勾稽)3.Commission 系→COMMISSION
     * (⚠ 报文里佣金行 transaction-type=Order + amount-type=Commission,故 Commission 检查**必须先于 Order**)
     * 4.FBA* / Storage Fee / ABA-* 描述类费用(同样挂在 Order 事件行下,须先于 Order)
     * 5.Order→SALE(剩余订单侧收入流:Principal/Tax/Shipping/GiftWrap/促销)
     * 6.其余→OTHER
     */
    private static UnifiedSettlement.FeeType feeType(String transactionType, String amountType, String amountDescription) {
        return switch (transactionType) {
            case "Transfer" -> UnifiedSettlement.FeeType.TRANSFER;
            case "Refund" -> UnifiedSettlement.FeeType.REFUND;
            default -> {
                if ("Commission".equals(amountType) || "RefundCommission".equals(amountType)) {
                    yield UnifiedSettlement.FeeType.COMMISSION;
                }
                String desc = StrUtil.nullToEmpty(amountDescription);
                if (desc.startsWith("FBA")) {
                    yield UnifiedSettlement.FeeType.FBA_FEE;
                }
                if ("Storage Fee".equals(desc)) {
                    yield UnifiedSettlement.FeeType.STORAGE;
                }
                if (desc.startsWith("ABA-")) {
                    yield UnifiedSettlement.FeeType.ADVERTISING;
                }
                if ("Order".equals(transactionType)) {
                    yield UnifiedSettlement.FeeType.SALE;
                }
                yield UnifiedSettlement.FeeType.OTHER;
            }
        };
    }

    /**
     * 金额解析:报告原值带符号;本地化小数格式拍板——EUR 站点 "1.234,56"(点=千分位,逗号=小数),
     * 其余币种 "1,234.56"(逗号=千分位,点=小数);千分位剔除后须可解析,失败即抛禁静默
     */
    private static BigDecimal parseAmount(String raw, String currency) {
        String value = raw.trim();
        boolean euroFormat = "EUR".equalsIgnoreCase(currency);
        value = euroFormat ? value.replace(".", "").replace(',', '.')
                : value.replace(",", "");
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("结算报告金额无法解析:" + raw + "(currency=" + currency + ")", e);
        }
    }

    /** 时间解析:全量时间戳(ISO8601 带时区)优先,date-only 回落当日零点 UTC(真凭证实测校准) */
    private static Instant parseTime(String raw) {
        String value = raw.trim();
        if (value.length() == 10) {
            return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(value).toInstant();
    }

    private static Integer integer(String value) {
        return StrUtil.isBlank(value) ? null : Integer.valueOf(value.trim());
    }

    /** 必填单元格缺失即拒(禁静默,docs/07 §8):带行号便于联调定位 */
    private static String required(String[] cells, Map<String, Integer> columnIndex, String column, int lineNo) {
        String value = cell(cells, columnIndex, column);
        if (StrUtil.isBlank(value)) {
            throw new IllegalStateException("结算报告第 " + (lineNo + 1) + " 行缺必填列 " + column + ",拒绝静默落库");
        }
        return value;
    }

    /** 按列名取单元格(容错越界:短行按空处理,列定位以表头为准) */
    private static String cell(String[] cells, Map<String, Integer> columnIndex, String column) {
        Integer index = columnIndex.get(column);
        if (index == null || index >= cells.length) {
            return "";
        }
        return cells[index].trim();
    }
}
