# #26 前端页面走查优化实施计划书

| 元信息 | 值 |
|---|---|
| TODO 条目 | #26 前端页面优化与 Bug 修复:**实际走查每个业务页面发现问题后登记并修复**,不预造 Bug 清单 |
| 优先级 | P2(持续型任务,按轮次推进) |
| 前置依赖 | 建议 sse-notify.md 先落地(SSE 断线重连是走查项之一);其余无 |
| 性质 | **走查 SOP + issue 登记修复流程**,非一次性开发计划 |
| 目标一句话 | 11 个业务域逐页走查,每轮产出 issue 列表→按影响面排序→逐项修复→门禁全绿收口 |
| 明确不做 | 视觉重设计/换主题风格;移动端适配(大工程 P3);新功能顺手加(走查中冒出的功能想法进 TODO.md 另立,不顺手改) |

## 一、背景与现状(2026-09-10 代码事实)

- 走查对象:`erp-web/src/views/` 14 个一级目录、25+ 业务页面——shop(店铺/pull-log/shop-product/shop-product-sku)、goods(product/category/brand)、order、inventory(inventory/flow)、purchase(order/inbound/supplier)、fulfill(delivery)、aftersale(order)、finance(exchange-rate/profit/profit-dashboard)、report(center/goods-analysis)、ai(chat/agent/kb/ai-suggestion)、system(user/role/menu/dict/config/notification)、warehouse(warehouse)、home。
- **已知坑先例(走查重点核对项)**:
  - vue-router5 out-in 过渡竞态致切页空白(docs/devlog/2026-09-07 有定位记录)——涉及路由切换的页面重点复测;
  - `<el-xxx>` 模板使用未 import:`scripts/check_el_imports.py` 可批量扫;
  - v-auth 语义:`perms` 为空=引导期放行,非空才严格判断(docs/09 §7)——新页面/新角色下权限边界重点走查;
  - 金额 DECIMAL(12,4) 按 string 禁浮点(docs/09 §6)——金额列/表单逐个核对;
  - 通知现为 60s 轮询(SSE 未落地前,断线重连项挂起)。
- 工程纪律:门禁四件(type:check/lint/lint:stylelint/build)全绿才算完成;本目录无独立 .git,husky 不生效,**门禁手动跑**;`tools/take-screenshots` 可作证据截图。

## 二、走查 SOP(每轮执行)

### 2.1 走查顺序(按业务链路,非目录序)

```
① 店铺域(shop*) → ② 商品域(goods*) → ③ 订单域(order) → ④ 库存域(inventory*)
→ ⑤ 采购域(purchase*) → ⑥ 发货域(fulfill) → ⑦ 售后域(aftersale)
→ ⑧ 财务域(finance*) → ⑨ 报表域(report*) → ⑩ AI 域(ai*) → ⑪ 系统域(system*/warehouse)
```
(上游数据是下游页面的输入,先修上游的展示错误,避免下游误报。)

### 2.2 每页走查清单(逐项过)

- **加载三态**:loading 骨架/空态/错误态是否都有且文案可懂;接口 500 时页面不白屏。
- **数据加载竞态**:快速切换筛选/翻页时旧请求是否作废(请求取消,base.ts 有取消机制)或至少结果不串页。
- **分页/筛选状态**:翻页后改筛选是否回第 1 页;返回列表页时状态是否保留(路由缓存语义,docs/09 §3 缓存约定)。
- **表单校验**:必填/长度/格式前后端一致;金额输入非数字/超精度行为;提交中防重复点击。
- **金额红线**:所有金额展示/提交为 string,禁 parseFloat/Number 运算(docs/09 §6)。
- **暗色主题**:逐页切暗色看对比度/边框/图表配色(数据看板类页面是重灾区)。
- **响应式**:1280 宽度下表格/表单不横向溢出(页面级 overflow-x 禁止)。
- **权限边界**:按 docs/09 §7 语义用受限角色账号过一遍——按钮显隐(v-auth)与页面可达(动态菜单)一致;越权直调接口由后端拦(前端只做展示裁剪,发现后端缺拦的记 issue 转后端)。
- **交互细节**:确认弹窗覆盖写操作;成功/失败反馈;hash 路由外链可达(#/ 形态)。
- **SSE/通知**(sse-notify 落地后):断线重连、多 tab 行为。

### 2.3 issue 登记格式

走查中发现的每个问题登记到 `docs/devlog/` 同级的 issue 清单(建议 `erp-web/docs/qa-issues.md` 或随轮次 `docs/plans/qa-round-<n>.md`,拍板落点):

```
## ISSUE-<n> <一句话标题>
- 日期/轮次:2026-09-xx / round1
- 域/页面:order / 列表页
- 复现:步骤(≤3 步)+ 截图(tools/take-screenshots)
- 期望 vs 实际:…
- 影响面:高/中/低(高=数据错误或不可用;中=体验受损;低=样式/文案)
- 修复:PR/commit 或"待排"
```

### 2.4 修复纪律

- 按影响面排序修:**数据正确性 > 功能不可用 > 体验 > 样式**;
- ≥3 文件同款改动用脚本/正则批量(范本 b0bbe68),禁逐文件手改;
- 同构页面新问题若暴露生成器模板缺陷:**修生成器 + 重新生成**,不逐页手补(生成器优先铁律);
- 修复会话每完成一轮跑门禁四件全绿才算收口;`GEN_OPENAPI_PATH=…` 冒烟回归仅涉生成器改动时跑。

## 三、轮次计划

| 轮次 | 范围 | 出口 |
|---|---|---|
| round1 | ①~④(店铺/商品/订单/库存) | issue 清单 + 高/中级清零 + 门禁绿 |
| round2 | ⑤~⑧(采购/发货/售后/财务) | 同上 |
| round3 | ⑨~⑪(报表/AI/系统) + 暗色主题专项过全部页 | 同上 |
| round4+ | SSE 落地后的通知链路 + 回归轮 | 同上 |

## 四、验收标准(每轮)

- issue 清单已产出且高/中影响项清零(低项可挂下轮);修复 commit 与 issue 编号对应。
- 门禁四件全绿(`pnpm type:check && pnpm lint && pnpm lint:stylelint && pnpm build`)。
- 未引入新同构手写样板(生成器能出的页面不许手写——修复中若重写页面,核对是否该走 gen:page)。

## 五、红线提醒

- 契约唯一查询源 `tools/openapi.json`,前端会话禁读后端 Java 源码;发现后端问题的走查记录交根目录会话处理,不在本目录动后端。
- 生成器产物区(tools/ 生成页面)修改走 Grep 定位+最小 Edit,禁整读回显;跨 ≥3 文件改动一律脚本批量。
- 不顺手加功能:走查发现的功能缺口登记 TODO.md 新编号,不混入修复 commit。
- 禁引入新依赖引库(如 markdown 渲染库等 P3 拍板项),修复以现有依赖能力为界。

## 六、交接边界

1. issue 清单落点(erp-web/docs/qa-issues.md vs docs/plans/qa-round-<n>.md)拍板。
2. 每轮修复由谁执行(执行模型逐 issue 修,每 issue 一个 commit 粒度便于回溯)。
3. 走查账号矩阵(管理员 + 受限角色各一套)由人工准备。

## 七、已执行轮次记录(round1~round7,2026-09-12~09-13,自 TODO.md 移入存档)

> 收门口径:门禁 oxlint 0 错 + oxfmt 归一 + vue-tsc 通过 + pnpm build 通过(涉后端另跑对应单测/validate 脚本);
> 「判非」= 查实非缺陷的澄清记录,防同疑问反复重查。

### round1(2026-09-12)39 页全量走查——4 Bug 全修

判定维度:console error + 接口 500 + 渲染空态三维。
①实时销售利润页白屏崩——`profitApi.page` 取 `page.records` 而后端契约 QueryPage 序列化为 `{list,total}`,
SKU 翻译 `res.list.map` 抛 TypeError 表格数据全丢,修为取 `list` 并就地定义 QueryPageResult 类型;
②资金流水页 `.trim is not a function`——`netCny` 后端 BigDecimal 序列化为 number,前端按 string 调 `.trim()`,
修为 `String()` 前缀判定 + interface 三金额字段改 number(展示层仍禁精度计算,docs/09 §6);
③供应商应付视图 500(后端)——`pageSupplierPayables` SQL 5 列 vs `SupplierPayableRow` 6 参构造器,
MyBatis 构造映射按列序对齐直接炸,补 `unpaidAmount` 外层计算列;
④发货单/入库单/订单/售后四页每次进入必报 `row.id 缺失` error——el-table hidden-columns 预渲染列插槽时
row 为空对象,expand 插槽加 `v-if="scope.row?.id != null"` 拦截(实测 v-if="scope.row" 拦不住空对象真值),
组件守卫保留纯防御;真实展开明细功能复验正常。
判非:调拨单 CanceledError=切页防竞态取消;部门/操作日志/平台回款空表=DB 无数据。
门禁:oxlint 0 错 / vue-tsc 通过 / erp-finance 单测绿 / 真机复验全绿。

### round2(2026-09-12)非 ProTable 手写页 + 公共件——9 处全修

①AI 对话流式回复"冻结"——占位 AI 行以 raw 对象 push,流式 chunk 改原始引用不触发响应式,改经响应式数组
取回 proxy 引用再追加;②AI 对话会话切换竞态(listMessages 序号守卫);③AI 对话页卸载 SSE 不断流
(chatStream 透传 AbortSignal,onUnmounted 断流,abort 静默不弹错);④利润看板 summary/trend/skuRank
三路并发竞态(序号守卫);⑤报表中心切 tab 不发请求——周报/SKU tab 复用日报数据且统计周期列空白
(el-tabs 无 @tab-change),接 onTabChange 即拉;⑥报表中心日报/周报/SKU periodRows 竞态(序号守卫);
⑦商品分析 fetchTrend 竞态(序号守卫);⑧系统设置 loadGroup 组切换竞态(序号守卫);
⑨全局 errorHandler 误弹「未知错误 cancel」通知——ElMessageBox 取消 'cancel'/'close' 字符串 reject
与业务错误 Result 对象经 Vue 异步事件链路路由到 errorHandler,改 `!(error instanceof Error)` 静默
(拦截器已提示),真 JS 异常照旧 notification。
判非:毛利率前端自算=文件头拍板设计(后端下发随 P3 拍板,#21 已落地);config 数值校验=后端
INT/LONG/DECIMAL/BOOL 已强校验;手写页"无 loading"=http 层默认全屏 loading;dept status 可清空=MP 缺省不更新语义。
验证:浏览器实测周报独立取数/取消零通知/暗色 0 白块/config 切 tab 无串组/AI 真回复。

### round3(2026-09-12)暗色主题专项 + 报表可视化调优——3 处修

暗色主题专项过全 11 域 40+ 页 + 公共件/布局硬编码色静态全量扫,浏览器实测:
①IconSelect(ProTable 图标选择器)硬编码边框 #dcdfe6/#4080ff 暗色下失真,改 var(--el-border-color)/
var(--el-color-primary);②商品分析 goodsTrend 真库 500(残留多日,暗色走查顺带暴露)——
selectSkuSalesTrend/selectSkuStockTrend 各只产 2 列 vs SkuTrendRow 三参 record,MyBatis 构造自动映射
按列序对齐缺列直接炸(同 round1 供应商应付 6 列先例),两条 SQL 各补 NULL 占位列凑齐 3 列;
validate_report_sql.py 5→7 项扩容覆盖双趋势真库形态(教训再证:改 SQL 当天必须重跑对应验证脚本,
单测 mock Mapper 测不出 XML 映射问题);③报表中心可视化调优(汇总卡 + SVG 趋势/Top15 条形/仓库构成
堆叠条 + 简报结构化排版,纯 CSS/SVG 零依赖全 CSS 变量暗色适配,数据前端内存聚合零后端改动),
顺带修 .table-box flex 压缩致图表底部裁剪(页面级滚动 + 卡片 flex-shrink:0)。
判非:SKU匹配/FBA发货单/采购单"金额列被操作列遮挡"= 列总宽>容器的正常横向滚动(滚动后全列可达,
固定列钉住无重叠);Menu/SubMenu、SearchMenu 的 #ffffff = 主色底白字明暗皆正确;底座水印字体色已
isDark 适配;暗色专项全 11 域逐页(含新增/详情弹窗、抽屉、展开行交互)零白块零可读性问题。
验证:浏览器实测商品分析出图无错弹;后端 erp-report 单测绿 + validate_report_sql.py 7 项全绿 + 新 jar 真机复验。

### round4(2026-09-12)表单校验边界 + 分页/筛选状态保持——4 处全修

静态扫 43 表单组件 + 浏览器实测:
①ProTable initParam 响应式变化重查不重置页码——深页码下切分类/筛选变窄后停留越界空白页,watch 改为
先 pageNum=1 再重查(惠及 goods/product 分类树与 finance/profit 筛选卡);②useTable.getTableList
空页回退守卫——末页数据被删空(删除/作废/他端变动)停留空白表格,结果空且 pageNum>1 自动回退上一页重取
(页码=1 兜底无死循环,中心化惠及全部列表页);③手工资金登记币种无归一——小写 "cny"/乱串原样入库致
回溯汇率解析失败留缺口,submit 前大写归一 + /^[A-Z]{3}$/ 拦截;④商品管理页窄视口(1032px)分类树被
挤压至 38px 不可见——公共 .table-main width:100% 在 row 布局下与树争宽且 ProTable 最小内容宽不可缩
(min-width:auto),树面板 flex-shrink:0 + 表格容器 min-width:0(列超宽交 el-table 自带横向滚动)。
判非:金额/数量/分摊/日期区间/词表校验前后端守卫齐备(ManualPaymentDialog 正数+4 位小数、
PurchasePaymentDialog 分摊行+同供应商、FirstLegShipDialog 运费/汇率>0+重量非负、el-input-number
全带 min/precision、datetimerange 自带起止顺序、registerManual direction/partyType 服务端词表校验);
useTable search/reset/sizeChange 均已回第一页。
验证:浏览器实测利润页查询回第 1 页/币种非法值拦截不发请求/商品页分类树 220px 恢复且切换分类正常,
全程 console 零 error;空页回退静态推演+页码=1 兜底(现有数据无法安全构造删空场景)。

### round5(2026-09-12)动态菜单权限边界 + 零数据空态专项——零缺陷收口

静态全量扫,纯静态审计零代码改动,无门禁/浏览器项:
①写操作按钮 v-auth 覆盖——页面级变更按钮要么挂 v-auth,要么按 AI 三页口径有意后端 admin 双闸
(ai/kb 文件头拍板,越权由后端拦截提示),弹窗内 submit 按钮继承入口门禁不重复挂;
②v-auth 键值 94 个唯一键与 01_schema_init.sql 菜单种子零漂移(键拼错=非 admin 永久隐藏功能不可达,
静默无报错,故种子一致性是权限边界的真风险面);③指令逻辑与 docs/09 §7 约定一致(perms 空集=
引导期放行,展示层裁剪 + 后端 @PreAuthorize 真闸);④看板/报表零数据除零边界——所有前端聚合
除法均有守卫(仓库堆叠 whMax Math.max(1,...) 下限、毛利率 sales>0 三元、占比 t<=0 判零)。
判非:加载态=round2 已口径(http 层全屏默认 + ProTable 共享 loading/empty);空表=DB 无数据非缺陷。

### round6(2026-09-13)资源泄漏 + 弹窗状态残留专项——5 处全修

静态扫定时器/监听器/观察器 33 处 + 30 弹窗抽屉,浏览器实测:
①ThemeDrawer 在 script setup 裸挂 window 'openThemeDrawer' 监听无清理——登出→再登录每次累积一个监听器
并滞留旧实例(改 onMounted 注册 + onBeforeUnmount 移除具名 handler);
②周期利润/③FBA发货单/④头程发货单三个详情抽屉 open() 重开不清旧 detail——加载期闪现上一单内容、
取数失败停留上一单数据误导(改先置 undefined 再取数,与 TransferOrderDetail 已验形态同构);
⑤SkuSelector 防抖定时器卸载不清——300ms 内关弹窗/切页后仍发起 2 个搜索请求(改 onBeforeUnmount 清 timer)。
判非:v-throttle/v-debounce/v-copy 三指令均有 beforeUnmount 清理、v-longpress 无清理但四指令全仓零使用
(不触发即无泄漏,不按「未使用代码」扩修);notification store 双通道 start/stop 已配对
(layouts onBeforeUnmount stop,登出即清轮询/SSE/visibilitychange);SearchMenu keydown 随 isShowSearch
开关配对(弹窗开着卸载的边缘由布局常驻兜底);SelectIcon/MoreButton/base.ts 三处 setTimeout 均一次性短时序无害;
表单弹窗 23 个 destroy-on-close 兜底、7 个非 destroy 弹窗 open() 手动重置逐一核过无残留;
ai-suggestion/kb 详情为同步赋值/先清后取无残留。
验证:浏览器实测主题抽屉开-关-重开、绑定弹窗 SKU 远程搜索正常,Console 零 error
(周期利润/FBA 列表无数据,残留清理由同构先例静态推演)。

### round7(2026-09-13)KeepAlive 缓存页数据新鲜度专项——2 处修复 + 顺带 2 处潜伏 500 全修

拍板:「仅 ProTable 列表页激活刷新」。
①ProTable 中心化 onActivated 激活刷新——缓存页切回自动重取当前页(保留筛选/页码;首次激活与
onMounted 查询重合跳过防双查);手写分析页(报表中心/看板/系统设置)有手动查询入口保持现状(拍板);
②useTable 请求序号守卫中心化——激活刷新/initParam 变更与在途慢响应竞态过期不覆盖(round2 同类修法
中心化收口,惠及全部列表页);③**FbaQueryMapper.pageRows 15 列 vs FbaShipmentResponse 18 参构造
自动映射缺列 500**(record 无 setter,有行即炸、空表/直跑 SQL 均不暴露,goodsTrend 同款坑第 3 次;
补 3 个 NULL 占位列凑齐,明细 List NULL 映射经真库+真机实证静默置 null)+ FirstLegQueryMapper.pageRows
同款潜伏(24 列 vs 26 参,补 2 NULL;开发库头程表无数据从未暴露)——全仓静态审计仅此两处
record-含-List resultType,其余列表页纯标量 record 线上有数据实证安全;④validate_fba_sql.py 5→6 项 /
validate_first_leg_sql.py 8→9 项:pageRows 构造映射参列数对齐静态断言(堵"空表验证测不出映射缺列"洞)。
判非:Main createComponentWrapper 按 fullPath 命名包装组件与 keep-alive include(path 字符串)匹配
——缓存机制正常(初判"include 装路径永不匹配"系漏看包装器,实锚澄清);Grid onActivated 仅 resize 用途。
验证:真机端到端(商品页 fetch 建单→切回 FBA 页自动出新行/删单→切回自动回 0,网络层实锤激活请求;
头程页 200 无 500;console 零 error)+ 两 validate 脚本全绿 + 新 jar 重启 8088。

### 后续待走查专项

- 安全:v-html 使用面/XSS 防护、token 存储与登出清理。
- 性能:路由懒加载核验、重复请求合并。
