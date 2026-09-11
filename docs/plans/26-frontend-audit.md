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
