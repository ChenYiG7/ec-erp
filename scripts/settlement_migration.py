#!/usr/bin/env python3
"""#19 财务/结算域立项 开发库对齐(2026-09-08):
   settlement_report/settlement_detail/exchange_rate 三表建表(三期 settlement 主线,
   docs/03 §6 定稿)。全部幂等(CREATE IF NOT EXISTS),与 docs/sql/01_schema_init.sql 逐字同源;
   连接信息读 local.properties(键=环境变量名),不含硬编码密码。跑完打印表数自检。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from menu_tool import connect  # noqa: E402

DDL = [
    """CREATE TABLE IF NOT EXISTS settlement_report (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    shop_id         BIGINT NOT NULL COMMENT '店铺ID(shop.id)',
    settlement_id   VARCHAR(64) NOT NULL COMMENT '平台结算批次号(Amazon SettlementId,幂等键,重拉 upsert)',
    period_start    DATETIME NOT NULL COMMENT '结算周期起(报告 StartDate)',
    period_end      DATETIME NOT NULL COMMENT '结算周期止(报告 EndDate)',
    currency        VARCHAR(8) NOT NULL COMMENT '结算币种(ISO 4217,Amazon 按站点单一币种,站点映射收口 AmazonMarketplace)',
    total_amount    DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '报告汇总总额(报告头 TotalAmount 原值含正负,勾稽基准=Σ明细金额)',
    fee_amount      DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '费用小计(Σ负项明细绝对值)',
    transfer_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '回款净额(Σ fee_type=TRANSFER 明细;预留金随真凭证实测校准)',
    raw_file_url    VARCHAR(512) NULL COMMENT '原始报告文件地址(S3 预签名URL会过期,仅审计留痕)',
    status          VARCHAR(16) NOT NULL DEFAULT 'PARSED' COMMENT '状态:PARSED解析入库(Σ明细=汇总校验平)/FAILED解析或勾稽不平(可重拉覆盖)',
    pulled_at       DATETIME NULL COMMENT '报告拉取时间',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_shop_settlement (shop_id, settlement_id),
    KEY idx_shop_period (shop_id, period_start)
) COMMENT='平台结算报告(结算周期正本,周期利润数据源;金额事件流水在settlement_detail)'""",
    """CREATE TABLE IF NOT EXISTS settlement_detail (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    report_id     BIGINT NOT NULL COMMENT '所属结算报告ID(settlement_report.id)',
    shop_id       BIGINT NOT NULL COMMENT '店铺ID(冗余自报告同事务写入,免联查)',
    order_id      VARCHAR(64) NULL COMMENT '平台订单号(Amazon OrderId原文;非订单事件如月租费/打款为NULL)',
    order_item_id VARCHAR(64) NULL COMMENT '平台订单行号(Amazon OrderItemId原文,SKU级利润归集键,对应shop_order_item.platform_order_item_id)',
    sku           VARCHAR(64) NULL COMMENT '平台SKU(报告原文,映射本地SKU随#5绑定关系联查,禁解析器猜)',
    fee_type      VARCHAR(32) NOT NULL COMMENT '费用类型(解析器归一,只加不改:SALE销售回款/REFUND退款/COMMISSION佣金/FBA_FEE履约费/STORAGE仓储费/ADVERTISING广告费/TRANSFER回款打款/OTHER其他)',
    amount        DECIMAL(18,2) NOT NULL COMMENT '金额(报告原值带符号:正=收入/负=费用,禁取绝对值,勾稽=Σ本列)',
    posted_at     DATETIME NOT NULL COMMENT '记账时间(报告 PostedDate)',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_report (report_id),
    KEY idx_shop_item (shop_id, order_item_id),
    KEY idx_posted (posted_at)
) COMMENT='结算报告明细行(金额事件流水;报告级幂等——重拉按report先删后插同#4拉单明细纪律,行级不设唯一键)'""",
    """CREATE TABLE IF NOT EXISTS exchange_rate (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    currency   VARCHAR(8) NOT NULL COMMENT '币种(ISO 4217)',
    rate       DECIMAL(18,8) NOT NULL COMMENT '汇率快照(1 currency = rate CNY;记账本位币V1固定CNY拍板,扩多本位币随四期评估)',
    quoted_at  DATETIME NOT NULL COMMENT '报价时间(利润折算口径=取 quoted_at<=业务日(下单日/记账日)的最近一条)',
    source     VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT '来源:MANUAL手工录入/API行情接口(随行情源接入评估,先手工维护)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_currency_quoted (currency, quoted_at)
) COMMENT='汇率快照(多币种折算依据;汇率是快照不是现值——折算一律按业务日回溯取数,禁取表内最新一条)'""",
]


def main():
    conn = connect()
    try:
        cur = conn.cursor()
        for sql in DDL:
            cur.execute(sql)
        conn.commit()
        # 自检:三表存在 + 索引齐全(uk_shop_settlement / idx_shop_item / idx_currency_quoted)
        cur.execute("SELECT COUNT(*) FROM information_schema.tables "
                    "WHERE table_schema='erp' "
                    "AND table_name IN ('settlement_report','settlement_detail','exchange_rate')")
        tables = cur.fetchone()[0]
        cur.execute("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                    "WHERE table_schema='erp' "
                    "AND ((table_name='settlement_report' AND index_name='uk_shop_settlement') "
                    "OR (table_name='settlement_detail' AND index_name='idx_shop_item') "
                    "OR (table_name='exchange_rate' AND index_name='idx_currency_quoted'))")
        indexes = cur.fetchone()[0]
        print(f"tables={tables}/3 indexes={indexes}/3")
        print("ALL GREEN" if tables == 3 and indexes == 3 else "CHECK FAILED")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
