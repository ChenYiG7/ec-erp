# #7 参数校验收口

- 日期: 2026-09-07
- 收尾提交: 见 feat(#7) 提交

## 范围盘点
- `TODO(编号)` 槽位全库清零后,仅剩 #7 参数校验一项纯代码可推进项(其余遗留均外部阻塞:
  #3 真凭证等账号 / #11 ship 编排随联调 / #12 财务勾稽三期 / #14 后续渠道 / #15 公开策略人工拍板)。
- 盘点法:全 Controller 扫 `@RequestBody` 无 `@Valid` 的端点(13 处)∩ 请求类无约束注解(7 类)。

## 落地
- **Brand/SysDict(纯配置域,Controller 直连实体)**: 实体加约束 + **分组校验**——
  Create 嵌套接口承载 @NotBlank(name/dictType/dictLabel/dictValue),@Size 进 Default 组(name 128/logo 512/remark 255/dict 64·128·128·255)。
  create 端点 `@Validated({Default.class, Xxx.Create.class})`;update 端点仅 `@Valid`
  ——**@NotBlank 对 null 也拒绝,不能进 Default 组**,否则拦掉 update 的 null-skip 部分更新语义(Controller 注释明示);
  @Size 对 null 放行,部分更新只拦"传了但超长"。
- **LoginRequest/PasswordChangeRequest/PasswordResetRequest**: @NotBlank + 端点 @Valid。
- **RoleMenuAssignRequest/UserRoleAssignRequest**: `@NotNull`(允许空列表=全量重绑清空语义,null 才是非法载荷)。
- **AftersaleHandleRequest 维持 Service 校验**: reject 必填/其余选填,统一 @NotBlank 会误伤 agree/refund/complete 的选填语义(docs/07 §1 校验收口 Service 的既有拍板)。
- 异常兜底零改动: Spring 7.0.7 实测 `MethodArgumentNotValidException extends BindException`(javap 验证),GlobalExceptionHandler 既有 BindException→400 直接覆盖。

## 坑
- Lombok `@Builder` 默认无 `toBuilder()`——测试想 `validBrand().toBuilder()` 改值直接编译失败;纯 builder 重造即可,不为测试改实体注解。
- apply_patch 多文件 hunk 中途失配会整体拒绝但**部分文件已改**: 大 patch 失败后必须逐文件核对实际落盘状态再续,不能按"全没生效"重放。

## 单测
- BrandValidationTest 6(分组行为: create 组必填/超长、Default 组 null 放行/超长仍拦/不担必填)。
- SystemRequestValidationTest 6(登录/改密/重置必填;两绑定入参空列表放行 + null 拒)。
- erp-goods 38 / erp-system 61 全绿;全 reactor compile 通过。
