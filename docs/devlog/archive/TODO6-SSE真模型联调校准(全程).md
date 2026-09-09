# TODO(#6) SSE 真模型联调校准(全程收口:DashScope qwen-plus)

- 日期: 2026-09-07
- 收尾提交: fix(两 bug) + fix(tools 分页 NPE) 两笔

## 接线真相(卡点复盘)
- 用户真 key/地址一直在 **Windows 用户环境变量**:OPENAI_BASE_URL=DashScope 兼容模式
  (https://dashscope.aliyuncs.com/compatible-mode/v1)+ OPENAI_API_KEY(sk-,35 字符)。
- yml 此前**硬编码 base-url=DeepSeek** 且只读 AI_API_KEY(local.properties 里是 14 字符旧占位值)
  → 阿里 key 打去 DeepSeek 必然 401,且从未有人怀疑"配置没问题"。
- 修复:yml 占位符化 OPENAI_BASE_URL / OPENAI_API_KEY(嵌套回落 AI_API_KEY)/ AI_MODEL,缺省回落 DeepSeek;
  local.properties 补 AI_MODEL=qwen-plus。Spring AI 2.0.1 的 OpenAI 支持基于官方 openai-java SDK
  (路径 = {base-url}/chat/completions),DashScope 的 /compatible-mode/v1 后缀恰好正确,无需路径覆盖。

## 校准结论(全链路实测)
- 帧形态:Spring SSE `data:` 帧、前导无空格、多行 chunk 编多 data: 行、无 [DONE];前端 chat.ts 零改动兼容。
- chunk 逐词增量推流 ✅;模型按工具描述自行决定调/不调(问"数店铺"时如实答无此工具,不编造)✅;
  只读工具闭环:qwen 发起 queryInventory → AuditingToolCallback 落 TOOL 行(工具名+入参 JSON)→
  真实数据回模型 → AI 行聚合落库 ✅;失败轮只留 USER/TOOL 行、不落 AI 行 ✅。
- 鉴权联调做法:JwtAuthenticationFilter 无状态,按 JwtTokenService 同构自签 dev token(HS256)。

## 联调炸出并修掉的三个 bug
1. **application.yml 空 mapping 登记块 = 启动即炸**(用户 IDEA 重启当场炸):全注释态子键 → Boot 4
   ConverterNotFound(String→配置对象)。规约:登记块至少留一个真键;单测覆盖不到绑定层,配 yml 改动必须真启动冒烟。
2. **流式错误穿透伪装 401 未登录**:chatStream 加 onErrorResume 转可见错误帧「【AI 调用失败…】(原因)」,
   失败轮不落 AI 行;端到端复验 HTTP 200 + data: 错误帧。
3. **四类 tools 分页参数 int 拆箱 NPE**(模型不传可选 pageNo/pageSize 时):
   统一 Integer + 空值回退 0,归一仍收口各 Filter record 的 page()/size();回归测试 4 个。

## 运维注意
- spring-boot:run 的 forked java 不随 mvn 死(TaskStop/Ctrl-C 后孤儿进程占 8088),重启前
  netstat 找 PID taskkill。
- spring-boot:run 工作目录默认 = 模块 basedir(erp-api),`optional:file:./local.properties` 落不到
  → ${ERP_JWT_SECRET} 解析不了会把 17 字符字面量当密钥炸启动;须 -Dspring-boot.run.workingDirectory=项目根。
