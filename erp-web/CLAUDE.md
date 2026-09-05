# erp-web 前端工作约定

Geeker-Admin v2 底座(Apache-2.0)的 EC-ERP 前端。**写任何前端代码前先读 `docs/09-前端开发规范守则.md`**(12 章铁律,本文不重复)。

## 会话与契约纪律

- 日常前端开发在本目录(`erp-web/`)下开 Claude 会话;根 CLAUDE.md 祖先链仍生效
- **契约唯一查询源 = `tools/openapi.json`**(`pnpm api:sync` 刷新);前端会话禁读后端 Java 源码
- 跨端契约改动(后端接口/表结构)回仓库根会话走 add-table/add-domain,不在本目录动后端

## 命令速查

```bash
pnpm dev                    # 起 5173(vite proxy /api -> 8088,禁 rewrite)
pnpm type:check             # vue-tsc,门禁一
pnpm lint                   # oxlint,门禁二(tools/** 已排除)
pnpm lint:stylelint         # 样式门禁;pnpm build 提交前必跑
pnpm api:sync               # 抓后端 /v3/api-docs -> tools/openapi.json(后端须已起)
pnpm gen:page --spec tools/specs/<domain>.txt   # 生成页面四件 + 菜单 SQL(存在即跳过)
```

- 门禁四件(`type:check`/`lint`/`lint:stylelint`/`build`)全绿才算完成;本目录无独立 .git,husky 不生效,**门禁手动跑**
- pnpm 传参给脚本用 `--` 分隔

## 生成器(新页面唯一正道)

- 新 CRUD 页一律:`api:sync` → 写 `tools/specs/<domain>.txt` 拍板表 → `gen:page` → 补 `TODO(#编号)` 槽位;**禁从零手写同构样板**
- spec 语法/生成物/边界见 `tools/README.md`;样板:`tools/specs/shop.txt`(带动作)、`tools/specs/shop-product.txt`(只读)
- 冒烟回归:`GEN_OPENAPI_PATH=tools/test/smoke-openapi.json node tools/gen-page.mjs --spec tools/test/smoke-spec.txt`(验后删产物)

## 高频契约事实(细节以 docs/09 §3 为准)

- `Result{code,msg,data}` 已在拦截器解包;双错误形态(HTTP 200+code≠200 / 真 401·403)收口 `src/utils/request/base.ts`,业务禁重复处理
- 分页:后端 pageNo/pageSize + records,前端模板 pageNum + list——**差异只在生成的 page 函数收口**
- JWT 24h 无 refresh token,401 即重登;金额 DECIMAL(12,4) 按 string 禁浮点
- `perms` 为空 = 引导期放行;v-auth 值 = 后端 permKey(如 `shop:add`)
