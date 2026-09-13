# TODO(#25) OSS对象存储(RustFS)

- 日期: 2026-09-11
- 关联: docs/plans/25-oss-storage.md(计划书);TODO.md #25 条目

## 拍板

- **sys_config 凭证例外扩大(docs/07 §7)**:SECRET 键族由「SMTP 授权码唯一例外」扩容为 +`erp.oss.secret-key`(#25 对象存储)——S3 SecretKey 与 SMTP 授权码同性质(部署侧基础设施凭证、单管理员内部系统 DB 明文已知悉),三道防线照抄 SMTP 口径:读侧 listByGroup 对 SECRET 统一回显 `******`、写侧掩码回环跳过、消费侧 valueOf 恒取真值。实现按 ValueType 泛化路数走,零硬编码 SMTP。
- **导出流程选方案 A**(2026-09-10 预拍板确认):保留同步流式导出体验,归档做成 `erp.report.export-archive` 开关默认关;开启而 OSS 未配置/上传失败 = 导出整体报错(开关是显式运维意图,禁静默吞档);URL 化下载(差距 G10)挂压测后拍板。
- **RAG 原文存档失败不阻断接入**:OSS 未启用(业务异常)静默走原链路,其余异常 log.warn 不中断——chunk 文本仍是重建正本,原文库是访问加速非 RAG 依赖(降级口径同向量化失败留 FAILED 的既有拍板)。

## 改动

- **erp-common 新增 `oss/OssService`**(全仓首个 AWS SDK v2 依赖,`software.amazon.awssdk:s3:2.46.7` 模块内自管版本,同 erp-report POI 先例;common → contract 新增依赖以 @Lazy 注入 SystemConfigApi,契约零依赖无环):upload(byte[]/InputStream)/download/presignGetUrl(默认 30 分钟)/delete/exists + report/kb/misc 三前缀 key 拼装(文件名消毒防 key 注入);配置读取 sys_config GROUP_OSS → yml `erp.oss.*` → 均无则 enabled=false 调用即抛"未启用";S3Client/S3Presigner 懒构建 + SystemConfigChangedEvent 监听失效重建(#18 先例);SDK 类型零外漏。
- **sys_config GROUP_OSS 五键**(enabled/endpoint/bucket/access-key/secret-key[SECRET])入 ConfigConsts 词表 + SystemConfigService(typeOf/keysOfGroup/groupOfKey)+ 01_schema_init.sql 种子段 + 前端系统设置页 OSS tab。
- **RAG 原文接入**:ai_kb_document 加 `original_file_key/original_file_size` 两列(01_schema_init/docs/03/实体三方同步);KbController.upload 传原始字节,KbIngestService.archiveOriginal 落 OSS;新增 `GET /api/ai/kb/documents/{id}/file` 返预签名 URL(读侧登录即可,仅已存档文档可用)。
- **导出归档**:ReportController 读 `erp.report.export-archive`(默认 false),开启时上传 `report/{yyyyMM}/{文件名}` 并附 `X-Archive-Key` 响应头,前端零改造。
- **同修存量 bug**:#29 上线的 GROUP_ORDER_REVIEW 在 SystemConfigService.keysOfGroup 的 switch 与前端参数组 tab 双双漏登(保存该组即抛"未知参数组"),本次补齐。
- **设施**:docker-compose rustfs 段补 RUSTFS_ACCESS_KEY/SECRET_KEY 环境变量与控制台端口说明(单端口 9000,S3 API 与控制台同口);.env.example 追加 RUSTFS 键;application.yml 增 `erp.oss.*` 占位符 + `erp.report.export-archive` 注释键。
- **验证**:全仓 mvn test 绿(erp-common OssServiceTest 5 例:key 拼装/未启用拒绝/配置优先级/事件失效/mock S3 四连);前端门禁四件绿;开发库真库执行 GROUP_OSS 种子 + ai_kb_document 两列 ALTER(幂等)并实查验证。

## 坑

- AWS SDK v2 的 `S3Presigner` 在 `services.s3.presigner` 子包而非 `services.s3`(编译期即报,好排);`PresignedGetObjectRequest` 的 Builder 无 `url()` 直设方法,单测构造须经 `httpRequest(SdkHttpRequest)` + `expiration/isBrowserExecutable/signedHeaders` 三件齐才能派生 `url()`——SDK 把 presigned 结果设计为不可手工伪造,合理但绕。
- RustFS 官方镜像控制台与 S3 API 同端口 9000(不是 MinIO 的 9001),#28 预留注释里"控制台端口随 #25 落地补"实际是"无需另开端口"。

## 未尽

- **真 RustFS 端点冒烟未做**(本会话环境无 docker):`docker run -d -p 9000:9000 -e RUSTFS_ACCESS_KEY=... rustfs/rustfs` 起单节点后,系统设置-对象存储 配五键,依次验 上传→下载→预签名→删除 四连 + secret-key 掩码回显;RAG 上传 .md 后 `original_file_key` 有值且预签名 URL 可下载。
- 导出归档/RAG 存档的存量环境对齐:其他环境执行 01_schema_init.sql 的 GROUP_OSS 种子段与 ai_kb_document 两列 ALTER(开发库已验)。
- 前端 `pnpm api:sync` 重抓契约快照(openapi.json 现为手改同步,#19/#29/#30 同口径待一次统抓)。
- 截图类资产仅预留 `misc/` 前缀;PDF/DOCX 解析进 RAG(P3 随 Tika)、多 Bucket 分域治理明确不做(计划书边界)。
