---
name: self-review
description: 提交前按本项目守则自查改动时使用。代码写完准备结束任务前、或用户要求检查/复查改动时加载,逐条核对 docs/07 检查点。
---

# 提交前自查清单(依据 docs/07,不复制其细节)

## 分层(docs/07 §2)
- [ ] Controller 无业务规则/多表/事务;**有 Service 的域,Controller 不注入 Mapper**(整域收口);dict/brand 纯配置域直连才合规
- [ ] 脱敏在 Service 读出口:密码/凭证字段任何返回路径清空
- [ ] 模块间无横向依赖,跨域调用在 erp-api 编排

## 数据(docs/07 §4~6)
- [ ] 多表写 `@Transactional(rollbackFor = Exception.class)`,注解在 Service
- [ ] 事务内无平台外呼/HTTP/推送(已移 afterCommit 或事件)
- [ ] 幂等靠唯一键 upsert,不是先查后插;库存只走 `InventoryService.change()`
- [ ] 金额 BigDecimal + DECIMAL(12,4),无 `new BigDecimal(double)`

## 可维护性(docs/07 §1/§9)
- [ ] 复杂逻辑/AI 代码 = `TODO(编号)` + 登记 TODO.md;无裸 TODO;占位抛 UnsupportedOperationException
- [ ] 无魔法值;异常消息无凭证/Token;新依赖已登记根 pom dependencyManagement
- [ ] 新接口已标 `@Tag`/`@Operation`(中文);分页入参继承 PageQuery

## 验证(docs/07 §10)
- [ ] `mvn -DskipTests compile` 通过
- [ ] 改动模块相关测试已跑过(erp-shop 现有 19 个)
