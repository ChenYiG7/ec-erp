#!/usr/bin/env bash
# 安静测试:全量日志落盘 target/,只回显测试摘要与失败详情,防日志刷屏省 token
# 用法: scripts/mvn-quiet.sh                 # 整仓 test
#       scripts/mvn-quiet.sh -pl erp-shop -am test
#       scripts/mvn-quiet.sh -DskipTests compile
set -uo pipefail
ARGS=("$@")
[ ${#ARGS[@]} -eq 0 ] && ARGS=(test)
LOG="target/mvn-quiet-$(date +%Y%m%d-%H%M%S).log"
mkdir -p target
mvn "${ARGS[@]}" >"$LOG" 2>&1
RC=$?
echo "== mvn ${ARGS[*]} → exit=$RC  全量日志: $LOG =="
if [ $RC -eq 0 ]; then
  grep -E '^\[(INFO|ERROR)\] Tests run:' "$LOG" | tail -15
  grep -E 'BUILD (SUCCESS|FAILURE)|Total time' "$LOG" | tail -3
else
  grep -E '<<< (FAILURE|ERROR)!' "$LOG" | head -20
  grep -E '^\[ERROR\]' "$LOG" | head -40
  grep -E 'BUILD (SUCCESS|FAILURE)|Total time' "$LOG" | tail -3
fi
exit $RC
