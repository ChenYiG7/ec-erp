#!/usr/bin/env bash
# 任务收尾脚手架:生成 docs/devlog 骨架,补拍板/坑/未尽各 2-3 句即可
# 用法: scripts/devlog-new.sh <TODO编号> "<标题>"
# 收尾后:CLAUDE.md 只更新目录速查表状态行,不再追加日期段落
set -euo pipefail
N="${1:?用法: devlog-new.sh <TODO编号> \"<标题>\"}"
TITLE="${2:?缺少标题}"
DIR="docs/devlog"
mkdir -p "$DIR"
SLUG="$(printf '%s' "$TITLE" | sed 's/[ /\\:*?"<>|]//g')"
FILE="$DIR/TODO$N-$SLUG.md"
if [ -e "$FILE" ]; then echo "已存在: $FILE"; exit 1; fi
COMMIT="$(git log -1 --format='%h %s' 2>/dev/null || echo '无')"
{
  echo "# TODO(#$N) $TITLE"
  echo
  echo "- 日期: $(date +%Y-%m-%d)"
  echo "- 收尾提交: $COMMIT"
  echo
  echo "## 拍板"
  echo "- (决策与理由,2-3 句)"
  echo
  echo "## 改动"
  echo "- (做了什么/影响面)"
  echo
  echo "## 坑"
  echo '- (踩坑与规避,没有写"无")'
  echo
  echo "## 未尽"
  echo '- (TODO 槽位/后续,没有写"无")'
} > "$FILE"
echo "已生成: $FILE — 补完四段后,CLAUDE.md 只更新目录速查表状态行"
