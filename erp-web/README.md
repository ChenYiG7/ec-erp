# erp-web — EC-ERP 前端

EC-ERP(自研电商 ERP)前端工程,基于 [Geeker-Admin v2](https://github.com/Geeker-Admin/Geeker-Admin) 二次开发(底座 commit `e3d8800809bd2f2f7e11c52b7aded0645a44c9cd`,2026-09-05 落地),按需裁剪与改造,规范见 `docs/09-前端开发规范守则.md`。

> **许可证**:Geeker-Admin v2 采用 [Apache-2.0](./LICENSE),本项目遵循其条款保留原始许可证副本,并在本文件声明二次开发及修改。

## 技术栈(2026-09-05 定版)

| 依赖           | 版本                      | 说明                                                 |
| -------------- | ------------------------- | ---------------------------------------------------- |
| Vue            | 3.5.x                     | Composition API + `<script setup>`                   |
| Vite           | 8.0.x(Rolldown)           | 落地即锁 pnpm-lock,禁追大版本                        |
| TypeScript     | 6.0.x                     | `pnpm type:check` 门禁                               |
| Element Plus   | 2.14.x                    | 锁 2.x 线,禁升 major                                 |
| Pinia          | 3.0.x(+persistedstate)    | token/user/dict/notification                         |
| vue-router     | 5.1.x                     | hash 模式;动态路由数据源 `GET /api/auth/me` menus 树 |
| UnoCSS         | 66.x(preset-wind4)        | 原子类                                               |
| oxlint + oxfmt | —                         | 替代 eslint/prettier                                 |
| pnpm           | 11.8(packageManager 锁定) | node ≥ 22.12                                         |

## 常用命令

```bash
pnpm dev            # 开发 :5173(vite proxy /api -> http://localhost:8088,禁 rewrite)
pnpm build          # 生产构建(vue-tsc && vite build)
pnpm type:check     # 类型检查
pnpm lint           # oxlint
pnpm lint:stylelint # 样式检查
pnpm test:unit      # vitest
pnpm api:sync       # 抓取后端 /v3/api-docs -> tools/openapi.json 快照(需先起后端)
pnpm gen:page       # 前端页面生成器(tools/gen-page.mjs,spec 驱动,存在即跳过)
```

> 提交发生在仓库根(erp-web 无独立 .git),husky/lint-staged 钩子不生效——门禁以手动跑
> `pnpm type:check` + `pnpm lint` + `pnpm build` 为准(docs/09 §11)。

## 目录速查

```
src/api/apis/       业务请求函数唯一居所(按后端模块分目录;组件禁直连 axios)
src/api/interface/  全局 API 类型中枢(对齐后端 response record)
src/views/          页面(动态路由 component 字符串相对此目录,如 system/user/index)
src/components/     ProTable/SearchForm/TreeFilter/Dict 等通用组件
src/stores/         pinia(user/auth/dict/notification/global/tabs/keepAlive)
src/utils/request/  axios 封装(Result 解包 + 双错误形态唯一收口)
tools/              代码生成器(api-sync / gen-page,零运行时依赖)
```

## 与后端联调

1. 起后端(:8088,详见仓库根 CLAUDE.md);
2. `pnpm dev` → 浏览器访问 :5173,登录(默认 admin,密码首次登录后立即修改);
3. 接口变更流程:`pnpm api:sync` → 看 `tools/openapi.json` diff → `pnpm gen:page` 或手改 api 层。
