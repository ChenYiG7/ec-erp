# TODO(#17) 产品描述生成V1(listing文案生成工作流)

- 日期: 2026-09-08
- 收尾提交: b2e977e feat: #17 落位表「产品描述生成」V1(listing 文案生成工作流)
- 前置: 本会话先提交了上一会话遗留的 9.7.2 SQL 原生形态收尾批(f5ed31b),再开本功能

## 拍板
- **降级语义与补货/异常/采购刻意不同**:那三个工作流建议本体是程序算的、LLM 只写报告,故"模板兜底照落库";
  文案的本体就是 LLM 产出,无模板可兜——无 key/调用失败/解析失败/逐商品漏回或必填(title/description)缺失
  → 该商品跳过不产出,degraded=true,**零垃圾建议落库**;llmMaxItems 超限按 productId 升序截断
  属成本护栏不算降级,截断商品下轮扫描自然补上(去重键=待确认存在)。
- **V1 不自动回填**:ai_suggestion 注释明言"人工确认后走业务接口,AI 禁直接写业务表",商品库无"更新 listing 文案"
  业务接口,平台改写更需 adapter 上架类接口——采纳仅确认,文案在详情 payload 复制使用;平台风格适配/自动回填 V2。
- **prompt 材料最小化**:只给 名称/品牌/类目/SPU 属性/SKU 规格(截前 20 行)——成本价/条码/HS 编码不进 prompt
  (防模型抄成本价当售价);spuCode 也不给(内部编码诱导编造)。

## 改动
- 契约扩容(GoodsQueryApi,只加字段不改语义):ProductFilter+status(扫启用商品)/ProductView+SkuView+attrsJson;
  新方法 listSkusByProductId/findBrandNameById/findCategoryNameById;实现收口 erp-api
  (类目委托 ProductCategoryService 新增 findNameById;brand 直连 BrandMapper——纯配置域直连即合规口径,同 BrandController 先例)。
- erp-ai graph/copywriting 七件套(CopyStateKeys/CopyItem/CopyCollectNode/CopyGenerateNode/CopyPersistNode/
  CopywritingWorkflow/CopyRunResult)+ CopywritingController(POST /api/ai/copywriting/run,登录即可,V1 不接定时);
  persist: type=COPYWRITING(词表既留槽位)/refType=GOODS_PRODUCT/refId=productId/summary=建议标题/risk 恒 LOW。
- 配置:ErpAiProperties.Copy 组(scan 护栏走 yml copy 段留真键防空 mapping 炸)+ sys_config 两键
  (erp.ai.copy.llm-max-items/prompt,AI 组白名单 + 系统设置页 CONFIG_ITEMS/PROMPT_KEYS 登记);
  **附带修复:sys_config 种子补上采购两键**——2026-09-08 采购提交只登记了前端 CONFIG_ITEMS,
  01_schema_init.sql 种子行遗漏(开发库已用 INSERT IGNORE 补齐,一次性脚本即删)。
- 单测 +17(erp-ai Collect 4/Generate 8/Persist 2/Workflow 3 + erp-api GoodsQueryApiImplTest 扩容 3);
  前端仅系统设置页两键登记(AI 建议页"文案"类型 enum 与 payload 详情抽屉早已预留,零改动)。

## 坑
- **同文件并行 Edit 写覆盖丢改复现**:CopyCollectNodeTest 五处编辑并行发出,helper 方法与两处桩替换被
  后写覆盖丢失,导致"找不到符号"+ 假失败(旧报告残留)——与 2026-09-07 01_schema_init.sql 同款坑,
  本文件后续编辑已全部改串行。
- **mock 同页无限翻页**:pageProducts(any()) 固定返回同一页时,扫描循环靠 rows.size()<pageSize 与
  scanMaxRows 护栏退出,scanned 被顶到护栏上限 200——分页扫节点的测试必须按页码打桩(页 2 起空页)。
- **mvn 在 agent bash 下失效**:Launcher 报 ClassNotFoundException,用 classworlds 直启
  (scripts/mvn-quiet.sh 未覆盖该环境);诊断跑 `-pl erp-ai test` 不带 -am 会拿到 ~/.m2 旧构件报雪崩假错,
  带模块过滤必须 -am。

## 未尽
- 定时接线待拍板(文案采纳是人工编辑节奏,V1 仅手动触发)→ TODO(#17)
- 平台风格适配(按站点/平台词表模板差异)与自动回填平台 listing → V2 随 adapter 扩容
- 商品库目前无文案质量评估面(采纳率/编辑率统计)→ 随报表域(四期 BI)再议
