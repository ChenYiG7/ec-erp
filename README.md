# ec-erp 自研电商 ERP(国内 + 跨境)

模块化单体架构。功能整合自启航(国内链路)、Wimoor(跨境纵深)、OmniTrade(AI 思路)。

## 技术栈

JDK 21 · **Spring Boot 4.0.6**(Spring Framework 7)· Spring AI **2.0.0-M5** · Spring AI Alibaba **2.0.0-M1.1**(Graph 工作流)· AgentScope Java **2.0.0-RC5**(多 Agent)· MyBatis-Plus 3.5.17(boot4-starter)· Hutool 5.8.47(工具库)· MySQL 9.7.2 LTS(SQL 9.7.2 原生口径,见 TODO"SQL 兼容性红线")· Redis 7 · Vue3 + TS + Element Plus

> 为什么必须 Boot 4.x:Spring AI Alibaba 2.0 与 AgentScope 2.0 都构建在 Spring AI 2.0(Spring Framework 7)之上,而 Spring AI 2.0 要求 Boot 4.x。三者是叠加关系,不是三选一。
> ⚠️ 三个 AI 依赖尚未 GA(SAI 2.0=里程碑 / SAA 2.0=里程碑 / AgentScope 2.0=RC),升级只动根 pom 的三个 version 属性。

## 当前状态

骨架 + 简单 CRUD 已编译通过;复杂逻辑与 AI 一律留 TODO 占位,逐项清单见 **[TODO.md](TODO.md)**,建表脚本见 `docs/sql/01_schema_init.sql`。

## 目录结构

```
ec-erp/
├── docs/                 文档(仅规约守则与建表脚本随仓库发布;01~06 设计文档、08、devlog 为作者私有,不随仓库发布)
│   ├── 07-开发规范守则.md
│   ├── 09-前端开发规范守则.md
│   └── sql/01_schema_init.sql
├── erp-common/           通用基础(返回体/异常/分页)
├── erp-system/           系统管理(RBAC/字典/通知渠道:站内/Webhook/邮件)
├── erp-shop/             平台中心(店铺/授权/拉取日志/多商户)
├── erp-goods/            商品中心(商品库/SKU绑定★)
├── erp-order/            订单中心(统一订单/审核/发货单)
├── erp-inventory/        库存中心(多仓/流水/补货规划)
├── erp-purchase/         采购中心
├── erp-warehouse/        仓储中心(出入库/盘点/头程)
├── erp-fulfill/          发货中心(面单/FBA/海外仓/物流商)
├── erp-aftersale/        售后中心
├── erp-finance/          财务中心(结算/利润/汇率)
├── erp-ads/              营销中心(广告/竞品/定价)
├── erp-report/           报表 BI
├── erp-platform-sdk/     平台适配防腐层(SPI + 各平台 adapter)
├── erp-ai/               AI 能力层(Spring AI 2.0 @Tool + SAA Graph + AgentScope,全为 TODO 占位)
├── erp-api/              主应用入口(:8088)
├── erp-worker/           拉单 worker(:8089,二期拆独立进程)
└── erp-codegen/          开发工具(非运行时):CRUD 五件套生成器,用法见其 README.md
```

## 快速开始

```bash
# 1. 建表:执行 docs/sql/01_schema_init.sql(幂等:CREATE TABLE IF NOT EXISTS + INSERT IGNORE,可重复执行)
#    (含 系统用户/角色/字典、RBAC 菜单三表、店铺、品牌/分类/SPU/SKU、SKU映射、订单、库存核心表;
#     二期表尚未建,见 TODO.md)
#    注:脚本 2026-09-02(shop 唯一键、绑定列可空)与 2026-09-03(11 表补 updated_at、inventory 补
#      created_at)修订过,此前已建库的旧库需手工补 ALTER,清单见 TODO.md #7(开发库已于 2026-09-03 执行并验证)

# 2. 本地配置:复制 local.properties.example 为项目根 local.properties(已被 .gitignore 忽略,严禁提交),
#    填入 MySQL/Redis 连接与密钥(ERP_JWT_SECRET / ERP_TOKEN_KEY)。application.yml 不含任何明文连接/密钥——占位符取自
#    local.properties 或同名环境变量(env 优先级更高,生产直接设环境变量即可);两者皆缺失则启动即失败
#    注意:datasource url 的 characterEncoding 必须写 UTF-8(Java 字符集名,驱动自动协商 utf8mb4),
#    写 utf8mb4 会连接报错

# 3. 构建并启动
mvn clean install
java -jar erp-api/target/erp-api-0.1.0-SNAPSHOT.jar

# 4. 登录:默认管理员 admin / admin@123(BCrypt 存储,首次登录后立即改密)
#    JWT 密钥必须设置:环境变量 ERP_JWT_SECRET(≥32字节),无默认值,不设置应用启动即失败
#    登录后请求头带 Authorization: Bearer <token>;全部 /api/** 已纳入登录鉴权,
#    用户/角色/菜单管理类接口限 admin 角色

# 5. 平台凭证加密密钥(TODO#2):ERP_TOKEN_KEY —— 无默认值,不设置应用启动即失败!
#    本地开发:写入项目根 local.properties 同名键(与 ERP_JWT_SECRET 同规,env 优先;2026-09-05 起支持,
#    此前只认环境变量);生成值须为 32 字节标准 base64:
#    Linux/macOS/Git Bash:  openssl rand -base64 32
#    Windows PowerShell:    $b=[byte[]]::new(32);[Security.Cryptography.RandomNumberGenerator]::Fill($b);[Convert]::ToBase64String($b)
#    生产:直接设同名环境变量。换密钥后存量密文不可解(店铺需重新授权),请妥善保管

# 6. AI 功能(可选,三期才实现):设置环境变量 AI_API_KEY 或改 application.yml
```

环境要求:JDK 21 · MySQL 9.7.2 LTS(mapper XML 自定义 SQL 用 9.7.2 原生形态并须真库验证,见 TODO"SQL 兼容性红线") · Redis 7。

## 开发工具:erp-codegen(CRUD 脚手架生成器)

新业务域标准流程:**表设计(add-table 流程)→ 跑生成器出五件套骨架 → 人工补业务规则(TODO 编号)**。

```bash
# 项目根目录执行;从 docs/sql/01_schema_init.sql 解析指定表,生成 Entity/Mapper/Service/Controller/单测
mvn -q -pl erp-codegen compile exec:java -Dtable=<表名> -Dmodule=<模块名> -DnameZh=<中文名> [-DtodoId=7] [-Dforce=true]
```

默认存在即跳过(不覆盖手工代码);参数与边界详见 `erp-codegen/README.md`。

## 开发约定(详见 docs/07-开发规范守则,基线:《阿里巴巴Java开发手册》)

1. 模块间禁止横向依赖,跨域调用在 erp-api 编排;
2. adapter 内只做报文翻译,不写业务逻辑;
3. 库存变更走统一入口,同事务写流水;
4. 金额一律 DECIMAL,平台幂等靠 `(shop_id, platform_xxx_id)` 唯一键;
5. 命名/分层/异常日志/事务/数据库/安全/单测/反模式清单,见 docs/07(以阿里巴巴Java开发手册为基线 + 项目铁律),写代码前先读。

包名 `com.own.erp` 可按需全局替换为自己的域名。
