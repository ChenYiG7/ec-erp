---
name: write-adapter
description: 写新平台 adapter 时使用。凡任务涉及新增平台对接、编写 XxxClient implements PlatformClient、OAuth 授权、拉单报文翻译、Token 刷新,先加载本流程再动手。
---

# 写新平台 Adapter 流程

## 前置必读
1. `docs/04-平台对接层设计.md` — 报文字段对照、网关/限流设计(本流程只给步骤,不复制其内容)
2. `erp-platform-sdk` 现有 SPI:`PlatformClient` / `AdapterRegistry` / `ShopSession` / `AuthToken` / Unified* 模型
3. `docs/07 §8 防腐层` — 红线所在

## 步骤
1. erp-platform-sdk 建 `com.own.erp.platform.adapter.<platform>` 包,`XxxClient implements PlatformClient`;`PlatformType` 枚举补值并注册 AdapterRegistry
2. 报文翻译:平台 JSON → `Unified*`;**出现 `if (业务)` 即防腐失败**(平台差异——状态枚举/币制/时区/面单——只许存在于 adapter)
3. `raw_json` 必存原始报文;核心字段成列,**禁为外部对象建全套镜像表**(wimoor 1688 教训)
4. OAuth:`buildAuthUrl`/`exchangeToken`;token 入库**必须复用 ShopService.createShop/updateShop 加密链路**(凭证写入唯一入口,禁旁路写凭证列);Token 刷新只在 erp-shop 授权中心,adapter 只消费 ShopSession
5. 拉单:按平台**"更新时间"窗口**(按下单时间会丢状态回传);游标 = pull_log 最近成功 `window_end` 左叠 5 分钟;幂等靠 `(shop_id, platform_order_id)` 唯一键 upsert,**禁先查后插**
6. 单测:用真实报文样本(脱敏)做翻译断言,**禁 mock 报文自嗨**;金额按 DECIMAL(12,4) 语义(平台最小单位整数已翻译成元)

## 完成检查
- [ ] adapter 内无业务 if、无 SQL、无直接访问业务表
- [ ] 日志/异常消息不含凭证与原始 token
- [ ] TODO(编号) 已登记/清理;docs/04 状态已更新
