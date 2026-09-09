# TODO(#0) Git历史去密与归并重写(公开前治理)

- 日期: 2026-09-05
- 收尾提交: a18bb1f chore: 更名ec-erp(artifactId/spring.application.name/文档同步)

## 拍板
- 项目要走"社区版公开+商密版闭源",公开前必须治理历史:GitHub 上 `ChenYiG7/erp` 已推送的 `初始化` 提交含真实 MySQL/Redis 密码与内网拓扑,拍板**删库重建**(仓库无 star/issue,零资产损失)而非 force-push(GitHub 旧 SHA 缓存仍可直链访问,删库才彻底)。
- 37 提交按主题归并为 14 提交(锁选型 Redis→ReentrantLock→Redisson 反转等过程噪音吸收进最终态),提交信息统一 `type(scope): 中文主题(TODO #N)` 简洁格式;统一作者身份 chenyi,弃历史中的 geyihang/apeloa 公司身份。
- 更名 own-erp→ec-erp 只做到 artifactId/spring.application.name/文档文案层;groupId `com.own.erp` 与 Java 包名不动(公开面不涉及,改动量与风险不划算)。代码内 `@author/@Date` 文件头保留(生成器约定产物)。

## 改动
- 重写方式:`git read-tree -u --reset` 链式重放(每新提交的树=原提交树原样拷贝,内容零手改,中间态天然编译一致);根提交取 `初始化2` 的树(yml 已环境变量化)→ 新历史从第一个提交起就无秘密;
- 安全阀:重写脚本内置四重不变式校验(新树与收口提交树 diff 为空/提交数=14/秘密值·内网IP·公司邮箱 `-S` 扫描 0 命中/单一提交身份),全过才删旧 master;`backup/pre-rewrite` 分支 + `../ec-erp-history-backup-20260905.bundle` 双备份(均只在本地,永不推送)。
- 更名收口:21 个 pom/yml + CLAUDE.md/README.md/docs/07 文案,`chore: 更名ec-erp` 单提交;全模块 `mvn-quiet test` 全绿后执行重写,重写后树与收口提交逐字节一致(免二次回归)。

## 坑
- 链式重放若用 `git restore`/`checkout -- .` 会在文件"后删前有"的区段留下幽灵文件(read-tree 才做整树替换);且重放前必须先收口工作区未提交改动(read-tree --reset 会无条件覆盖 worktree)。

## 未尽
- ~~GitHub 删库重建 + 推送~~ → 第二轮执行(见下)。**凭证轮换仍未做**:MySQL root 密码与 Redis 密码已公开多日,即使历史清掉也必须轮换,新值只进 local.properties。

## 第二轮(2026-09-05 追记)
- 拍板:`docs/` 整体不入公开仓库(设计/建表脚本/devlog 均商密),`.gitignore` 声明 + CLAUDE.md 标注"严禁 git add -f";第二轮重写把 docs/ 从全部 15 个提交的树中剥离(devlog 单文件提交剥离后为空,跳过),收口提交 `chore: docs目录不入公开仓库`。
- 拍板:旧历史备份(backup/pre-rewrite 分支 + bundle)按人工指令直接删除,删除前先从 git 对象恢复被第二轮 checkout --orphan 冲掉的 TODO0 devlog 文件,随后 reflog expire + gc --prune=now 物理清除全部旧对象。
- 坑:`git checkout --orphan <起点>` 会把"旧 HEAD 有、起点树没有"的跟踪文件从磁盘删掉——第一轮的 TODO0 devlog 只存在于 devlog 提交不在根树,被无声删除,靠 master 引用未删才得以恢复;凡"只在新提交里"的本地文件,重写前先确认也在更早的树里或先备份。
- 坑:`git diff` 对非 ASCII 路径默认八进制转义+引号包裹,`grep -v '^docs/'` 滤不掉,须 `-c core.quotePath=false`;另 `git checkout` 切到含磁盘未跟踪同名文件的树会拒绝,内容一致时 `-f` 安全。
