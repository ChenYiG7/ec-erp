# TODO(#10) 供应商名称唯一性收口

- 日期: 2026-09-07
- 收尾提交: 待提交(本会话只落工作区)

## 拍板
- 唯一性两层:友好校验在 Service(save/update 查重上抛"供应商名称已存在",update 排除自身,
  部分更新 name=null 跳过),硬约束收口表 `uk_name`——并发窗口漏网由唯一键兜底
  (docs/07 §5"幂等靠唯一键"同款纪律;先查后插的禁令针对幂等落库,友好报错校验不属此列)。

## 改动
- docs/03 supplier 段标注 name UNIQUE;01_schema_init.sql supplier 加 `UNIQUE KEY uk_name (name)`
  (CREATE IF NOT EXISTS 幂等,已建库需手工 ALTER——先 `GROUP BY name HAVING c>1` 清重再加键,
  SQL 已登记 TODO #10 条目)。
- SupplierService:requireNameFree(name, excludeId) 私有查重件,save/update 接线,清除遗留 TODO 注释。
- 单测 +2:save 重名拒(不触 insert)/ update 改成他人名拒 + 保持原名放行(排除自身,合一用例)。

## 验证
- 全 reactor `mvn test` BUILD SUCCESS,12 个含测模块合计 521 全绿,erp-purchase 48→50。
