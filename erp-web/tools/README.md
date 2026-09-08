# erp-web 前端代码生成器

移植后端 erp-codegen 理念(存在即跳过 / 守卫前置 / spec 即拍板表 / TODO 槽位),零运行时依赖(Node 22 内置 fetch/fs/path)。

## 两个命令

| 命令                                                      | 作用                                                                                        |
| --------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| `pnpm api:sync`                                           | 抓后端 springdoc `/v3/api-docs` → `tools/openapi.json` 快照(前端契约唯一查询源,入库可 diff) |
| `pnpm gen:page --spec tools/specs/<domain>.txt [--force]` | 按行式 spec + 快照生成页面四件 + 菜单 SQL                                                   |

```bash
# 标准顺序(新页面)
pnpm api:sync                                        # 1. 刷新契约快照,git diff 核账
vim tools/specs/<domain>.txt                          # 2. 写拍板表
pnpm gen:page --spec tools/specs/<domain>.txt         # 3. 生成(存在即跳过)
# 4. 人工补 TODO 槽位 + 执行打印的菜单 SQL(核对 id 后)并回写 docs/sql/01_schema_init.sql
pnpm type:check && pnpm lint                          # 5. 门禁
```

- **存在即跳过**:四件任何一件已存在就 `SKIP`,人工改动安全;`--force` 才覆盖(覆盖前先 diff)。菜单 SQL 永远只打印不落盘。
- **守卫前置**:缺快照 / spec 缺头 / 未知键 / 未知 role / `todoId` 未登记 TODO.md / chat 模式五端点缺一,一律报错退出(带行号),禁静默降级。
- **冒烟自测**(不碰真快照与业务页面):

  ```bash
  GEN_OPENAPI_PATH=tools/test/smoke-openapi.json node tools/gen-page.mjs --spec tools/test/smoke-spec.txt       # crud 模式
  GEN_OPENAPI_PATH=tools/test/smoke-openapi.json node tools/gen-page.mjs --spec tools/test/smoke-chat-spec.txt # chat 模式
  # 验证后删除 src/{api,views}/smoke 等产物
  ```

## 生成物四件 + 菜单 SQL

| 产物                                        | 说明                                                                                                                   |
| ------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `src/api/interface/<module>/<domain>.ts`    | Response / SaveRequest / Query 类型,字段注释取 openapi description;嵌套 `$ref` 展开为具名 interface;**禁手抄后端字段** |
| `src/api/apis/<module>/<domain>.ts`         | `<entity>Api`:page/detail/create/update/remove/动作;**分页差异(pageNo/records ↔ pageNum/list)只在 page 内收口**        |
| `src/views/<module>/<domain>/index.vue`     | ProTable 页面:列/搜索/操作列 v-auth/动作 TODO 槽;`defineOptions` name = 路由 name(KeepAlive 前提)                      |
| `src/views/.../components/<Entity>Form.vue` | 新增/编辑共用弹窗:required rules / enum select / dict / money 注释;成功 emit `saved`                                   |
| 菜单 SQL(stdout)                            | `INSERT IGNORE INTO sys_menu`;spec 声明 `menuId=` 则可直接执行,否则模板等人工分配 id;核对后回写 01_schema_init.sql     |

**chat 模式(pageType=chat)产三件**(会话页模板,#6;母本 views/ai/agent):interface(typesFrom 复用再导出 + 角色词表)/
apis 五函数(pageSessions/createSession/listMessages/chatSync/chatStream,SSE 收口 `utils/sse` postSse)/ 会话页
(角色切换 radio 仅角色模式);无 Form、无按钮权限行;五端点先在快照核账缺一报错。

## spec 语法(行式,`#` 开头为注释行)

```text
# ── 头段(必填:module/domain/entity/nameZh/permPrefix/todoId)──
module=shop            # 后端模块(决定 api/interface 目录)
domain=shop            # 域名(缺省路径段探测用)
entity=Shop            # 实体名(PascalCase,派生 ShopApi/ShopResponse/ShopForm)
nameZh=店铺管理        # 页面/菜单标题(缺中文打警告)
permPrefix=shop        # 按钮 permKey 前缀:shop:add / shop:edit / shop:remove
todoId=16              # 必须已登记 TODO.md 的 `## #16 `,生成的槽位注释用它
menuParent=1           # 菜单挂载父 id(缺省 0)
menuId=100             # 可选;声明则菜单 SQL 可直接执行,按钮 id = menuId*100+n
path=/shop             # 路由路径,同时是 ProTable pageId(缺省 /<domain>)
component=shop/index   # 相对 src/views 的组件路径(缺省 <module>/<domain>/index)
base=shop-products     # 可选;基路径段显式声明(探测顺序:base > domain 同名段 > module 同名段 > 报错)
icon=Shop              # 可选;菜单图标
menuSort=3             # 可选;菜单同级排序 sort(缺省 1)
readonly=true          # 可选;只读域:不产 Form/写接口/工具栏按钮
pageType=chat          # 可选;chat=会话页模板(SSE 流式),缺省 crud
chatBase=/api/ai/agents/{role}  # chat 必填;端点前缀,角色模式含 {role} 占位(与快照路径字面一致,五端点核账)
typesFrom=ai/chat      # chat 必填;契约类型复用源 <module>/<domain>(模板不自产会话类型,禁重复手抄)
emptyText=/placeholder=  # chat 无角色模式的空态/输入提示(角色模式文案走 role= 行)

# ── 字段行 ──
field=<prop>  role=all  label=店铺名称  dict=shop_platform  width=120  required
#   role:      search / column / form / all(search+column+form)
#   label:     中文标题(缺省回落字段名并警告)
#   dict:      字典类型(下拉选项走 dict store,ProTable enum 函数形态)
#   enum:      静态枚举 值:标签[:tagType],多值 | 分隔;数字形态自动转 Number(filterEnum 严格相等)
#   width:     列宽数字
#   裸标记:    money(金额,类型按 string 且禁浮点) / required(表单必填) / hide(列不渲染) / tag(标签样式)
#   dict 与 enum 互斥同用报错

# ── 动作槽位行(key 匹配不到字段行时按页面级动作处理)──
todo=authUrl:GET /{id}/auth-url 后 window.open,回调在 8088 直出 HTML

# ── 会话页角色行(仅 pageType=chat;文案可含空格)──
role=SUPPORT  name=客服助手  empty=<空态文案>  placeholder=<输入提示>
role=OPS      name=运营助手  empty=...  placeholder=...
#   无 role= 行 = 无角色会话页(端点无 {role},页面无角色切换)
```

字段类型一律以 openapi 快照为准,spec 只声明**展示角色**;spec 字段若不在响应 schema 中会警告(列恒空)。

## 目录

```
tools/
├── api-sync.mjs        # pnpm api:sync
├── gen-page.mjs        # pnpm gen:page
├── lib/
│   ├── fsutil.js       # writeIfAbsent(存在即跳过;统一 LF + UTF-8 无 BOM)
│   ├── openapi.js      # 快照加载 / $ref 解析 / 基路径探测 / 端点分类(GEN_OPENAPI_PATH 测试覆盖)
│   ├── types.js        # schema→TS;未知形态 throw 禁静默 any
│   ├── spec.js         # 行式 spec 解析,报错带行号
│   └── render.js       # 四件渲染 + 菜单 SQL
├── specs/              # 业务域拍板表(入库可评审)
│   ├── shop.txt
│   └── shop-product.txt
├── test/               # 冒烟夹具(生成器回归自测,非业务)
│   ├── smoke-openapi.json
│   └── smoke-spec.txt
└── openapi.json        # 契约快照(pnpm api:sync 产出;首跑前不存在属预期)
```

## 边界(生成器不做什么)

- 跨域动账、复合事务动作的调用编排、树形/子表页面布局 → 一律 TODO(编号) 人工
- chat 模板不自产契约类型(typesFrom 必填复用既有域接口文件);非 ai 系无共享类型源的新会话域暂人工,拍板后再扩模板
- 菜单 SQL 不直连 DB,人工核对 id 后执行并回写 `docs/sql/01_schema_init.sql`
- 未归类端点(如非 `/api/<base>` 前缀、无 `{id}` 动词形态)只警告不生成
