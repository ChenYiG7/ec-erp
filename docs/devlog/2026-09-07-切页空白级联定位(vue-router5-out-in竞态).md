# 切页空白级联定位复盘(vue-router 5 × transition mode="out-in" 竞态)

- 日期: 2026-09-07
- 影响面: erp-web 布局层 `src/layouts/components/Main/index.vue`(全站页面切换)
- 修复: 移除 `<transition mode="out-in">` 的 `mode`(改并发过渡),保留 fade-transform 动画与 KeepAlive
- 回归工具: `erp-web/scripts/page-sweep.mjs`(Playwright 自动登录 + 全站快切 + 空白检测)

## 症状特征(高辨识度,见此可直奔本档)

1. **SPA 内切换数页后内容区整片空白**:`.el-main` 的 innerHTML 只剩一个注释节点 `<!---->`(v-if/空渲染占位),菜单/面包屑/tabs 一切正常;
2. **级联**:空白一旦发生,之后**所有**页面切换都空白,不是单个页面的问题;
3. **无报错**:console 无 error、无 Vue warn、无 pageerror,网络面板无请求(组件根本没挂载);
4. **F5 整页刷新恢复**,再快切又复现——典型的"会话内状态被污染"而非代码数据错误;
5. 首空点与**切换速度**相关(快节奏连切触发,慢速单切难复现),且稳定从某页开始级联(本次 = 平台商品页,纯属该轮切换序列的累计时序,该页本身无辜)。

## 定位过程(对照实验二分,每步排除一个假设)

工具: playwright(msedge channel,系统浏览器免下载)+ 项目自带 @playwright/test。先写 sweep 脚本稳定复现(25 站连续 goto,每站检测 `.el-main` 子节点数/文本长度),再逐层做对照:

| # | 假设 | 实验 | 结论 |
|---|------|------|------|
| 1 | Tabs「刷新」的 isRouterShow 卡 false(refresh(false) 后未恢复) | blank 时经 `__vueParentComponent` 读 MainContainer 实例 setupState | `isRouterShow=true`,排除 |
| 2 | createComponentWrapper 的 wrapperMap 缓存丢 render | 同上,遍历 wrapperMap | 全部 hasRender=true,排除 |
| 3 | router-view 插槽 Component 为空 | createComponentWrapper 插桩 `!component` 分支 | 从未触发,排除 |
| 4 | wrapper 机制(`h(component)` 闭包) | 禁用 wrapper,`:is="Component"` 直连 | **仍复现**,排除 |
| 5 | **transition mode="out-in"** | 整体移除 `<transition>` | **不复现,定根因** |

方法要点:**DOM 现场先看一眼**(innerHTML 的 `<!---->` 直接指明渲染链产物是占位),再用"改一处跑一遍"的对照实验二分,不要靠读码空想——本案 5 个假设里前 4 个看起来都更"像",全被实验排除。

## 根因

`<transition mode="out-in" appear> + <keep-alive :include> + <component :is :key="route.fullPath">` 组合在 **vue-router 5**(项目 ^5.1.0;Geeker 上游为 vue-router 4)下的竞态:out-in 要求"旧组件 leave 完成后再 enter 新组件",连续导航时 leave→enter 链路被中断,enter 永不执行,后续所有切换在 Transition 层被吞(渲染为占位注释)。首次触发后就地污染 Transition 状态,故级联;F5 重建组件树故恢复。

## 修复与取舍

- 修复 = 去 `mode` 改**并发过渡**(新旧页面动画短暂重叠,视觉可接受),appear 一并去除;
- 备选被否:完全去 transition(丢动画)、去 KeepAlive(缓存刚需)、降级 vue-router 4(大动作, §1 升级纪律需拍板)。

## 关联改动(同日)

- `docs/09` §12 反模式表追加一行(布局层 out-in × keep-alive 组合 → 并发过渡);
- 同日另修的两类前端坑(gen:page 页面漏 import El 组件、同 URL 并发请求互 abort)见 `scripts/check_el_imports.py` 日志,均为"运行时警告/静默失败"族,可用 page-sweep + console 过滤一并兜底。
