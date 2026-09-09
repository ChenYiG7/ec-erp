# TODO(#16) 前端工程立项 Geeker-Admin v2 落地

- 日期: 2026-09-05
- 收尾提交: e9b9ec5(P1+P2)/ bb02bca(P3)/ ee96490(P4)/ f3e8238(P5)/ fa8ea77(P6 挂起说明)/ 本次(P6 页面 + P7 收尾)

## 拍板
- 底座选型 = Geeker-Admin v2(`Geeker-Admin/Geeker-Admin@main`)而非 vben 5/soybean/自建:ProTable 配置化 CRUD 与后端
  八件套生成器理念同构(页面=配置对象),单应用轻量、中文文档、自带动态路由+按钮权限正好接 sys_menu/perms;
  vben 5 monorepo 重且默认 ant-design-vue 已约两年未发版,soybean 主 UI Naive UI 在重表格 ERP 场景生态偏弱。
- **许可证实测 Apache-2.0(非对比文章所标 MIT,v1 才是 MIT),用户确认接受**——erp-web/ 保留上游 LICENSE 副本 +
  README 声明基于 Geeker-Admin 二次开发;本仓库自身 LICENSE(#15 路线 A ④)不受影响,届时独立拍板。
- 位置 = 本仓库根 erp-web/(纯 Node 工程不进根 pom,门禁双轨:后端 mvn-quiet.sh / 前端 pnpm);
  规范 = docs/09-前端开发规范守则.md 公开入库;生成器 = erp-web/tools/ 移植 erp-codegen 理念
  (openapi.json 快照驱动、存在即跳过、守卫报错、TODO(编号) 槽位、行式 spec 即拍板表)。
- 会话纪律:日常前端开发在 erp-web/ 下开 Claude 会话(搜索半径限定前端,根 CLAUDE.md 祖先链仍加载);
  契约唯一查询源 = erp-web/openapi.json,前端会话禁读后端 Java 源码(最大输入减负);跨端契约改动回根目录会话。
- **P6 挂起解除(2026-09-05 晚)**:用户起通远端 MySQL/Redis + local.properties 补 ERP_TOKEN_KEY(根因修复:
  CryptoService 读键改 Spring 占位符)+ 起 erp-api → `pnpm api:sync` 出真快照,真契约核账发现并修掉生成器 4 个
  形态盲区后,六域四件全部落地。

## 改动
- TODO.md:新增 #16 立项条目(P0~P7 阶段清单+验收线);勾掉 #7/#8 前端散项、#14 通知中心与 #10/#11 createdBy 散项补"随 #16"。
- P2:axios 双错误形态收口 `src/utils/request/base.ts`(200+code≠200 / 真 401·403);登录+动态路由(auth store
  按 /me menus 重建,menuType 3 不进路由、permKey 后端扁平化);通知 60s 轮询(visibilitychange 暂停恢复)+ Header 弹层。
- P3:`tools/{api-sync,gen-page}.mjs` + lib 五件(fsutil/openapi/types/spec/render);specs/ 行式拍板表;
  冒烟夹具 `tools/test/`(GEN_OPENAPI_PATH 离线回归,不碰真快照)。
- P6 六域落地:shop(四件+authUrl OAuth 按钮人工接线)、shop/shop-product(readonly 三件)、system-user(四件 +
  UserRoleDialog 角色分配 + ElMessageBox.prompt 重置密码;user.ts 手写 changePassword 与生成 sysUserApi 并存)、
  system-role(四件 + RoleMenuDialog 授权树:回显只勾叶子防父子矛盾,保存合并半选)、system-dict(四件 +
  clearDict 强刷接线;dict.ts DictApi 与 sysDictApi 并存)、system-menu(**树形例外页全手写**:NONE 分页 +
  数组 requestApi + $attrs 透传 default-expand-all;表单 menuType 联动显隐 + el-tree-select 防自环)、
  goods-product(readonly 三件 + TreeFilter 分类树人工接线,categoryApi 手写)。
- 菜单种子收敛回写 01_schema_init.sql:id=7 对齐 spec(/goods/product + goods/product/index);新增 100 字典/101 平台商品
  + 按钮段 200~242(system:user:*/system:role:*/system:menu:*/shop:*/system:dict:*)+ sys_role_menu;
  另补 shop_platform 字典种子 14 条(dict_value = PlatformType 枚举名);已建库均留手工执行注释。

## 坑
- 选型对比文章(2026-04 CSDN 全景图)将 Geeker 标为 MIT——实测 v2 package.json/LICENSE 为 Apache-2.0,
  MIT 是 HalseySpicy v1 线的信息;选型前必须实测上游 package.json 而非采信二手对比文。
- v2 实际版本线比多数网络资料新一档(Vite 8 Rolldown/TS 6/Pinia 3/vue-router 5/oxlint+oxfmt),
  v1 教程(axios 封装在 src/api/helper/ 等)路径全部对不上,一切以落地实际目录树为准。
- **冒烟夹具盖不住真快照形态**——真契约核账连抓 4 个生成器盲区(均已在 render.js/openapi.js 修复):
  ① 分页参数是对象形态(query 参数 $ref 到 XxxQuery/PageQuery),非扁平 pageNo/pageSize,还有
  GET /system/dicts 的**混合形态**(PageQuery 对象 + 扁平 dictType),按扁平判定会整域丢分页;
  ② 仅分页无详情的域(角色),records 的 $ref 在 items 里,读 records.$ref 得 null → Response 类型整个缺失;
  ③ 商品详情返回 Result<Map> 聚合形态,Response 取数必须**分页行优先**、detail 只做兜底;
  ④ 同域同名动作撞键(GET+PUT /users/{id}/roles)→ 冲突时加方法前缀(getRoles/putRoles);
  另有 rules 多字段缺逗号(冒烟单 required 字段盖不住)。教训:**生成器离线冒烟过了 ≠ 真快照过关,首接真契约必核账**。
- springdoc 对 GET 的 POJO 入参渲染成单个对象参数(非扁平展开),是 ① 的根因;后端不拆参数不改,
  生成器适配(对齐"adapter 内只做翻译"精神:形态差异在生成器单点消化)。
- --force 重生成会按设计冲掉人工接线(dict.ts 的 DictApi 并存、dict 页 clearDict、商品页 TreeFilter)
  ——人工改动前先 diff;两个 api 文件的"生成+手写并存"格局已在文件头注释写明重生成后需重新并入。

## 未尽
- **后端写链路 500(阻塞 UI 全链路验收)**:读全通;POST /system/dicts、/system/roles 等统一
  `{"code":500,"msg":"系统繁忙"}`——疑与当日后端 WIP(CryptoService 占位符改造 + 多个 Service @Lazy 构造环修复)或
  远端库 schema 漂移有关,待后端排查;前端拦截器对 500 走形态一弹窗,行为正确。
- 菜单 icon 渲染体系待核对:sys_menu 种子 icon 存的是小写串(setting/user/peoples…),SelectIcon 是 EP 组件名 +
  svg 文件双体系,现有 svg 目录仅 10 个旅行主题图标——渲染效果待浏览器验收,必要时统一迁 EP 名并更新种子。
- 浏览器 UI 手工链路(P7 清单:角色授权重登录生效/店铺 auth-url 真实跳转/通知已读/字典联动)待写链路修复后走查。
- 商品分类管理页(brands/categories CRUD)与 SKU 绑定动作页随 add-domain 后续迭代;goods-product spec 已留 TODO 槽。
- 本仓库自身 LICENSE 选择(#15 路线 A ④)仍待公开时拍板。
- erp-web/.claude/settings.json 硬约束 deny 后端目录:默认不上,嫌前端会话误读后端时再启用。
