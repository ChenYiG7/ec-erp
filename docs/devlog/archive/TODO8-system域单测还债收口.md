# TODO(#8) system域单测还债收口

- 日期: 2026-09-05
- 收尾提交: 6476dab TODO(#12) 平台售后同步upsert:saveUnifiedRefund唯一写入口(uk_shop_platform_refund幂等upsert+ShopOrderApi扩findIdByPlatformOrderId契约翻译order_id,订单未入库跳过重拉)+状态映射拍板(平台状态只做首插初始映射,已存在单仅平台终态REJECTED/CANCELLED条件推进未决态单=预留撤单出口,人工状态机主线不被回退);aftersale新增AftersaleOrderMapper.xml;单测+7全模块绿;售后拉单Job接线随#3真凭证

## 拍板
- 测试方向拍板:前端立项 vs 单测还债二选一,选单测还债——前端是数天量级方向性工程(留给用户拍板技术栈再立项),
  单测缺口半天量级且 #8 可就此关闭单测条目。
- PasswordEncoder 用真实 BCrypt 低代价轮次(new BCryptPasswordEncoder(4))而非打桩:加密/比对走真算法,
  matches 语义与落库哈希一致性被真实覆盖,符合 AIR"可重复"且单次 encode 约 5ms 可忽略。
- AuthServiceTest 把"用户不存在与密码错误同一文案"作为显式断言(两分支取 unknown.getMessage() 与 wrongPwd 对比):
  防用户名探测是安全语义,重构成两文案时单测应报警。

## 改动
- erp-system 新增 5 测试类 45 用例:SysUserServiceTest 15(三条专用密码通道/用户名唯一/updateUser 物理隔绝 password)、
  SysRoleServiceTest 5(删角色仍绑用户即拒+菜单绑定清理)、SysMenuServiceTest 14(组树父不在集合按根容错/
  绑定先删后插 InOrder/更新禁自环/删菜单子级拦截)、AuthServiceTest 6(登录防探测/禁用 403/JWT 载荷装配//me 无 token)、
  JwtTokenServiceTest 5(签发解析往返/防篡改/悬挂 Base64 严格预校验/弱密钥启动拦截)。
- "其余每个 Service ≥1 个"全模块盘点达成:34→39 测试类,goods 翻新域已有 14+12,brand/dict 纯配置直连豁免;#8 单测条目关闭。
- docs/07 §10 补"MP Wrapper 急切解析坑"记载(.in() 急切解析列元数据 + LambdaUpdateWrapper.set() 同款,
  #5/#11/#14/#8 四处先例收拢);TODO.md #8 单测条目勾选。
- erp-system 单测 8→53,全模块全量绿。

## 坑
- JJWT `signWith(Key)` 按密钥长度自选最强算法:JwtProperties 默认密钥 50 字节 → HS384(HMAC-SHA384,签名段 64 字符),
  不是想当然的 HS256/43 字符——悬挂 Base64 用例硬编码 +2 追加后 %4==2 可解码,落到 SignatureException 而非
  IllegalArgumentException 被当场抓获。修复:按实际段长补齐到 %4==1,禁硬编码段长(算法随密钥长度变,测试要跟着变)。
- SysMenuService 已绑定角色的分支(treeByUserId 过滤禁用/listRoleKeys 启用过滤/listPermKeys 去重)走 `.in()`,
  纯 Mockito 不可直测(#5/#11 先例),仅覆盖 CollUtil 空集合短路分支,测试类 javadoc 已注明边界——
  该坑 TODO #11 声称 docs/07 §10 已载实际缺失,本次一并补齐。

## 未尽
- menu/auth 的 in 查询已绑定分支无单测(坑所述 MP 限制);若后续引入测试专用的 TableInfo 初始化机制可放开。
- JwtTokenService 过期语义(expire-hours)未测(需可控时钟,当前实现无时钟注入点);接按钮级鉴权(perm_key)时一并评估。
- #8 剩余:前端工程未创建、Apifox 未正式订阅(随前端立项收口)。
