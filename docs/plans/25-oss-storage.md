# #25 OSS 对象存储(RustFS)实施计划书

| 元信息 | 值 |
|---|---|
| TODO 条目 | #25 引入 RustFS(S3 兼容)替代本地文件存储,统一管理 Excel 导出/RAG 文档原文/截图 |
| 优先级 | P2(无外部依赖,可立即开工) |
| 前置依赖 | 无;docker-compose 编排部分与 #28 合并落地 |
| 目标一句话 | erp-common 落地统一 `OssService`(S3 协议),sys_config 热更配置面,三类资产(Excel 导出/RAG 原文/截图)按计划逐步接入 |
| 明确不做 | PDF/DOCX 解析进 RAG(P3 随 Tika);截图类资产本计划只留接入位(当前全仓无图片资产面);多 Bucket 分域治理 |

## 一、背景与现状(2026-09-10 代码事实)

- **Excel 导出=零磁盘足迹**:`erp-report` 的 `ReportService` 用 POI `XSSFWorkbook` → `ByteArrayOutputStream` → `byte[]`,Controller 以 `ResponseEntity<byte[]>` + Content-Disposition 直接流式回浏览器(端点 `GET /api/report/export/sales`、`/export/inventory`)。**不存在"本地文件迁移"问题,OSS 化是加法**。
- **RAG 文档原文不保留**:`KbController.upload` 把 `file.getBytes()` 按 UTF-8 转 String(仅 .txt/.md/.markdown,上限 `erp.ai.kb.max-file-bytes`=1MB),chunk 文本存 `ai_kb_chunk.content`(即重建正本);`ai_kb_document` 只有元数据列,无原文存储。向量索引 `KbVectorIndex` 以 JSON 文件持久化(`erp.ai.kb.index-path`,默认 `data/ai/kb-index.json`)。
- **相邻先例**:`settlement_report.raw_file_url` VARCHAR(512) 存 S3 预签名 URL 仅审计留痕;`SpApiReportsClient` 有预签名下载链路(仓库唯一"对象存储相邻"代码)。
- **空白面**:erp-common 无 OssService,无任何 S3 SDK 依赖;sys_config 无 OSS 键族。

## 二、方案设计

### 2.1 依赖与落点

- S3 客户端:AWS SDK v2 `software.amazon.awssdk:s3`(对 RustFS 的 S3 兼容 API)。版本随 Spring Boot BOM 管理不了(Boot BOM 不含 aws),**erp-common 自管版本**(先例:POI 仅 erp-report 依赖模块内自管,根 pom 注释版本)。
- `OssService` 放 **erp-common**(TODO 已拍板),**对外不泄漏 SDK 类型**:方法签名只用 `byte[]/InputStream/String key`,S3Client 为私有字段。

### 2.2 配置面(sys_config 热更 + yml 兜底)

- sys_config 新键族 GROUP_OSS:`oss.enabled` / `oss.endpoint` / `oss.bucket` / `oss.access-key` / `oss.secret-key(ValueType.SECRET,回显掩码)`。
- **红线冲突预警**:`docs/07 §7` 规定 sys_config 禁入凭证类键,SMTP 授权码是**唯一**范围例外。本计划把 secret-key 加进例外 = **扩大例外范围,必须 devlog 记拍板**(三道防线照抄 SMTP 口径:ValueType.SECRET 掩码回显、SECRET 掩码、不进日志/契约)。
- 兜底:`application.yml` 增加 `erp.oss.*` 占位符(local.properties 可覆盖),OssService 读取顺序 sys_config → yml → 未配置时 `oss.enabled=false` 直接抛业务异常"对象存储未启用",不静默。热更失效走 `SystemConfigChangedEvent` 监听重建客户端(#18 先例)。

### 2.3 OssService API 面

```
String upload(String key, byte[] data, String contentType)
String upload(String key, InputStream in, long size, String contentType)
byte[] download(String key)
String presignGetUrl(String key, Duration ttl)   // 默认 30 分钟
void delete(String key); boolean exists(String key)
```

- key 规范:`report/{yyyyMM}/{文件名}`、`kb/{documentId}/{原始文件名}`、`misc/{yyyyMM}/{uuid}-{文件名}`。统一在 OssService 内拼前缀,业务方只传语义名。

### 2.4 三类资产接入口径

| 资产 | 接入方式 | 拍板点 |
|---|---|---|
| Excel 导出 | **保持同步流式为默认**;新增"归档到 OSS"开关(`erp.report.export-archive`,默认 false),开启后上传 OSS 并在响应头附 `X-Archive-Key`。改预签名 URL 下载模式(响应体换 url)涉及前端 blob 流程改造,挂 G10 压测后拍板 | 导出流程 A(流式不变+归档)vs B(直接 URL 化) |
| RAG 文档原文 | 上传时原文字节存 OSS(key 记入 `ai_kb_document` 新列),向量索引链路不变;新增原文下载端点(预签名 URL) | 无(方案已定) |
| 截图/静态资源 | 仅预留 `misc/` 前缀与 OssService 能力,不建页面 | 无 |

### 2.5 RustFS 本地设施

- 本地开发:RustFS 单节点 Docker(rustfs/rustfs 镜像),compose 片段写入 #28 的 docker-compose.yml(本计划只交付 compose 片段文本,#28 落盘)。

## 三、实施步骤

1. **erp-common**:pom 加 aws sdk s3(版本注释)→ 新建 `oss/OssService`(plain @Service,配置读取+客户端懒构建+SystemConfigChangedEvent 失效)→ yml 占位符。
2. **sys_config 键族**:种子 SQL(GROUP_OSS 五键,ValueType 标注)→ SystemConfigService 侧 SECRET 掩码回显核对(#14 SMTP 同构,核对现有实现是否按 GROUP 泛化,若硬编码 SMTP 需小改并说明)。
3. **RAG 原文接入**:add-table skill 给 `ai_kb_document` 加列(`original_file_key` VARCHAR(512)、`original_file_size` BIGINT)→ KbController.upload 落 OSS → 新增 `GET /api/ai/kb/documents/{id}/file` 返预签名 URL。
4. **导出归档开关**:erp-report 读 `erp.report.export-archive`,开启时 OssService 上传 + 响应头 `X-Archive-Key`;不改前端。
5. **单测 + 冒烟**:单测 mock S3Client;集成冒烟本地 RustFS docker 起一台上传/下载/预签名/删四连。
6. **devlog**:sys_config 凭证例外扩大拍板 + RustFS 选型一句话。

## 四、表结构草案(最终以 add-table skill 落 01_schema_init.sql)

```sql
-- ai_kb_document 加列(ALTER 幂等写法按 add-table skill 惯例)
ALTER TABLE ai_kb_document ADD COLUMN original_file_key VARCHAR(512) NULL COMMENT 'OSS 对象键(原文存储,#25;NULL=未存原文)';
ALTER TABLE ai_kb_document ADD COLUMN original_file_size BIGINT NULL COMMENT '原文字节数(#25)';
```

## 五、验收标准

- `mvn -DskipTests compile` 通过;erp-common 单测(OssService 键拼装/未启用抛错/配置失效重建)绿。
- 本地 RustFS + 真库冒烟:sys_config 配好后上传下载预签名全通;secret-key 接口回显为掩码,库内为明文值(同 SMTP 口径)。
- RAG:上传 .md 后 `original_file_key` 有值,预签名 URL 可下载原文;关 OSS(oss.enabled=false)上传走原链路不受影响。
- 导出:开关关闭行为与现状逐字节一致;开启后 OSS 内文件与响应头 key 一致。

## 六、红线提醒(按本项裁剪)

- **sys_config 凭证边界**:docs/07 §7,secret-key 入库必须 ValueType.SECRET + 掩码回显,且 devlog 拍板记录例外扩大;禁入任何提交的配置文件与日志。
- OssService 禁把 S3 SDK 类型出现在返回值/参数(防腐同款纪律);业务模块禁直连 SDK。
- `Result` 解包/错误形态照旧(docs/09 §3);新增端点 Controller 标 @Tag/@Operation(中文)。
- 无 SQL 兼容面(仅两列 ALTER),但仍走 add-table 三方同步(脚本↔docs/03↔实体)。

## 七、交接边界(必须人工拍板)

1. 导出流程 A/B(A=流式+归档开关,推荐先 A;B 挂 G10)。
2. sys_config 凭证例外扩大(SMTP 唯一 → +OSS secret-key)的 devlog 拍板。
3. 生产 RustFS 部署形态(单节点 vs 集群)与容量规划——本计划只覆盖本地开发与 compose 编排。
