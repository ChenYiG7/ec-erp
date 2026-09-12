# #28 工程化部署(一键本地 + Docker)实施计划书

> ✅ 已于 2026-09-11 实施(dev-up.sh 一键脚本 + Maven 多阶段 Dockerfile + docker-compose 编排
> erp-api/MySQL/Redis + 前端 Nginx 镜像 + .env.example 模板,随 commit e00f24e 入库)。
> 工程底座类不单列 devlog,实施事实见该 commit 与 README 部署节。

| 元信息 | 值 |
|---|---|
| TODO 条目 | #28 一键本地部署(scripts 启动脚本)+ Docker 部署(Dockerfile + docker-compose 四服务) |
| 优先级 | P2(无外部依赖) |
| 前置依赖 | 无;RustFS 服务编排吸收 docs/plans/25-oss-storage.md 的 compose 片段 |
| 目标一句话 | 新环境 `docker compose up -d` 即跑通全链;新开发者跑一条脚本完成建库→配置检查→构建→启动(后端,可选前端) |
| 明确不做 | CI/CD 流水线、生产级编排(K8s/资源限制/监控告警)、erp-worker 拆分部署(一期单进程跑全部 Job) |

## 一、背景与现状(2026-09-10 代码事实)

- **全仓零 Docker 残留**(无 Dockerfile/compose/Jenkinsfile),纯新建。
- 配置加载链:`erp-api/src/main/resources/application.yml` 占位符 ← `spring.config.import optional:file:./local.properties`(模板 `local.properties.example`,OS env 优先);MySQL/Redis 走 `${MYSQL_*}`/`${REDIS_*}` 占位。
- 密钥 fail-fast 双闸:`ERP_JWT_SECRET` 在 yml 无默认值(占位符解析失败即启动失败)+ `JwtTokenService` 构造期校验 ≥32 字节;`ERP_TOKEN_KEY` 在 `erp-shop` 的 `CryptoService` @Value 占位符读取。**校验友好报错脚本要做在启动前**,Spring 的报错栈对新人是噪音。
- 构建:根 pom 19 模块,运行物只有 `erp-api-0.1.0-SNAPSHOT.jar`(:8088,单进程含全部 9 个 Job);前端 erp-web 纯 Node 工程不进根 pom(`pnpm build` 产 dist)。
- scripts/ 现有惯例:mvn-quiet.sh / devlog-new.sh 等 **bash 脚本**(win 下 git-bash 执行),README.md「运行」章节(L122~142 一带)已有手工步骤可作蓝本。
- MySQL 9.7.2 LTS + Redis 7;建库正本 `docs/sql/01_schema_init.sql`(CREATE IF NOT EXISTS 幂等,可重复执行)。

## 二、方案设计

### 2.1 一键本地部署(scripts/)

- 脚本:`scripts/dev-up.sh`(bash,与现有脚本同族;Windows 用户走 git-bash——**拍板点:是否额外出 PowerShell 版**,默认不做)。
- 步骤编排:
  1. 环境检查:`ERP_JWT_SECRET`/`ERP_TOKEN_KEY` 缺失或 <32 字节 → 友好报错并列 local.properties 写法示例后退出;MySQL/Redis 端口连通性探测(fail 即提示检查 local.properties)。
  2. 建库:用 mysql 客户端执行 `docs/sql/01_schema_init.sql`(幂等;脚本不内置密码,从 local.properties 读 `MYSQL_USERNAME/MYSQL_PASSWORD`)。
  3. 构建:`scripts/mvn-quiet.sh -DskipTests compile` → `mvn -pl erp-api -am -DskipTests package`(参数 `--skip-build` 可跳)。
  4. 启动:`java -jar erp-api/target/erp-api-0.1.0-SNAPSHOT.jar` 前台起(参数 `--with-web` 另开窗口/后台起 `pnpm dev`)。
- 幂等可重入:重复执行不重复建库(脚本幂等)、不重复构建(有 jar 且 `--skip-build` 时跳过)。

### 2.2 Docker 部署

- **Dockerfile**(仓库根,多阶段):
  - 阶段1 `maven:3.9-eclipse-temurin-21`:全仓 `mvn -DskipTests package`(可选构建参数 `MAVEN_OPTS`/镜像源 ARG,国内网络用)。
  - 阶段2 `eclipse-temurin:21-jre`:拷 erp-api jar,`ENTRYPOINT java -jar`,EXPOSE 8088。时区 `TZ=Asia/Shanghai`(Job cron 全按本地时区)。**镜像内不烘焙任何密钥**。
- **docker-compose.yml**(仓库根)四服务:
  - `mysql`:tag 用 `mysql:9.7`(9.7.2 具体小版本 tag **执行时上 Docker Hub 核实**,不存在则 `mysql:9` + digest 钉版);volume 挂数据;首次启动挂载 01_schema_init.sql 到 `/docker-entrypoint-initdb.d/`;healthcheck `mysqladmin ping`。
  - `redis`:redis:7-alpine,volume 挂 AOF/RDB。
  - `rustfs`:rustfs/rustfs 镜像(#25),volume 挂数据,`--profile oss`(可选启用)。
  - `erp-api`:build .,env 注入 `ERP_JWT_SECRET/ERP_TOKEN_KEY/MYSQL_*/REDIS_*/OPENAI_*`,depends_on mysql/redis healthy。
- **前端形态拍板点**:独立 Nginx 镜像(推荐:不侵入后端打包;hash 路由**无需** history 回退配置,nginx.conf 只需 `location /api { proxy_pass http://erp-api:8088; }` 禁 rewrite)vs 嵌入 erp-api 静态资源。默认按 Nginx 镜像写,`--profile web` 可选。
- 密钥注入:根目录 `.env`(gitignore)+ `.env.example`(提交,占位值);compose `env_file` 引用。**红线:.env 禁入 git,提交前必须核对 .gitignore**。
- 健康检查:erp-api 无 actuator 依赖(现状),healthcheck 用 `wget -qO- http://localhost:8088/v3/api-docs >/dev/null || exit 1` 做存活探测即可,不为此引 actuator。

## 三、实施步骤

1. `scripts/dev-up.sh` + 环境检查逻辑(独立函数,失败信息含修复示例)。
2. 根 `Dockerfile`(多阶段)+ `.dockerignore`(target/、node_modules/、docs/、.git/、erp-web/node_modules)。
3. 根 `docker-compose.yml`(mysql/redis/erp-api 必选,rustfs=profile oss,web=profile web)+ `.env.example` + `.gitignore` 补 `.env`。
4. `erp-web/Dockerfile`(node:22 构建 dist → nginx:alpine)+ `erp-web/nginx.conf`(proxy /api → erp-api:8088,**无 rewrite**)。
5. README「快速开始」重写:两条路(Docker 一键 / 本地 dev-up)各 ≤15 行,密钥准备指向 .env.example 与 local.properties.example。
6. 冒烟:清空 docker volume 后 `docker compose --profile web up -d`,浏览器登录 → 动态菜单 → 任一页面取数。

## 四、表结构草案

无(纯工程任务,唯一 DB 交互=执行既有 01_schema_init.sql)。

## 五、验收标准

- 新人视角:clone → cp .env.example .env 填两个密钥 → `docker compose --profile web up -d` → 5 分钟内登录成功,订单/库存页面正常取数。
- dev-up.sh:在无 jar、无库的干净环境一次跑通;缺密钥时报错含修复指引而非 Java 堆栈。
- 重复执行 dev-up.sh / compose up 幂等;`docker compose down -v` 后可重来。
- compose 内 erp-api 的 9 个 Job 正常触发(观察 pull_log/日志时间戳),MDC traceId 正常。

## 六、红线提醒

- **密钥纪律**:.env/local.properties/任何真实密钥禁入 git;镜像分层不得 COPY 密钥文件;docs/07 §7。
- compose 的 mysql 初始化只允许 01_schema_init.sql 单一正本,**禁止**在 initdb 目录塞其他迁移脚本(增量 schema 一律走 add-table skill 维护正本)。
- MySQL 9.7.2 镜像 tag 与驱动兼容性执行时真机核实(本项目 mapper 按 9.7.2 原生形态写,降版 MySQL 会踩 SQL 兼容红线)。
- 时区:所有容器 `TZ=Asia/Shanghai`,否则 Job cron 与日期轴错位(先例:SalesSnapshotJob 日期轴事故教训)。

## 七、交接边界

1. 前端 Nginx 镜像 vs 嵌入 erp-api(计划默认 Nginx,拍板可翻)。
2. dev-up.sh 是否补 PowerShell 版(win 原生开发者体验)。
3. 生产部署形态(资源限额/restart 策略/日志轮转/备份)不在本期,后续按实际部署环境另立。
