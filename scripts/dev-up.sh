#!/usr/bin/env bash
# 一键本地部署:#28 —— 环境检查 → 建库 → 构建 → 启动(后端,可选前端)
# 用法:
#   scripts/dev-up.sh                 # 全流程:检查+建库+构建+前台启动后端
#   scripts/dev-up.sh --skip-build    # 跳过构建(已有 erp-api/target/*.jar 时直接启动)
#   scripts/dev-up.sh --with-web      # 另起前端 pnpm dev(后台,日志 target/web-dev.log)
#   scripts/dev-up.sh --skip-db       # 跳过建库步骤
# 依赖:bash(git-bash/WSL/Linux)、JDK 21、mvn;建库需 mysql 客户端(连接信息读 local.properties)
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1
ROOT_DIR=$(pwd)
LOCAL_PROPERTIES="$ROOT_DIR/local.properties"
SCHEMA_SQL="$ROOT_DIR/docs/sql/01_schema_init.sql"
JAR="$ROOT_DIR/erp-api/target/erp-api-0.1.0-SNAPSHOT.jar"

SKIP_BUILD=0
WITH_WEB=0
SKIP_DB=0
for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=1 ;;
    --with-web)   WITH_WEB=1 ;;
    --skip-db)    SKIP_DB=1 ;;
    *) echo "未知参数: $arg(支持 --skip-build / --with-web / --skip-db)"; exit 1 ;;
  esac
done

# ---------- 工具函数 ----------

fail() { echo ""; echo "❌ $*"; exit 1; }
ok()   { echo "✅ $*"; }

# 读 local.properties 的键值(env 优先);值可能含中文占位,原样返回
prop() { # $1=key
  local v="${!1:-}"
  if [ -z "$v" ] && [ -f "$LOCAL_PROPERTIES" ]; then
    v=$(grep -E "^${1}=" "$LOCAL_PROPERTIES" | head -1 | cut -d= -f2- | tr -d '\r')
  fi
  echo "$v"
}

# TCP 连通性探测(git-bash /dev/tcp)
probe_port() { # $1=host $2=port
  (exec 3<>"/dev/tcp/$1/$2") 2>/dev/null || return 1
  exec 3>&- 3<&- 2>/dev/null
  return 0
}

# ---------- 1. 环境检查(密钥 fail-fast 前置,Spring 的报错栈对新人是噪音)----------

echo "== 1/4 环境检查 =="

JWT_SECRET=$(prop ERP_JWT_SECRET)
TOKEN_KEY=$(prop ERP_TOKEN_KEY)

fix_example() {
  echo ""
  echo "   修复方式(二选一):"
  echo "   a) 写入项目根 local.properties(模板见 local.properties.example):"
  echo "      ERP_JWT_SECRET=<至少32字节随机串,生成: openssl rand -hex 32>"
  echo "      ERP_TOKEN_KEY=<32字节标准base64,生成: openssl rand -base64 32>"
  echo "   b) 或设置同名环境变量后重跑本脚本"
  echo ""
}

if [ -z "$JWT_SECRET" ]; then
  echo "缺少 ERP_JWT_SECRET(JWT 签名密钥,无默认值,应用启动即失败)"
  fix_example
  exit 1
fi
if [ ${#JWT_SECRET} -lt 32 ]; then
  echo "ERP_JWT_SECRET 长度 ${#JWT_SECRET} 字节,不足 32 字节(JwtTokenService 构造期校验会拒绝启动)"
  fix_example
  exit 1
fi
ok "ERP_JWT_SECRET 已就绪(${#JWT_SECRET} 字节)"

if [ -z "$TOKEN_KEY" ]; then
  echo "缺少 ERP_TOKEN_KEY(平台凭证 AES 加密密钥,无默认值,应用启动即失败)"
  fix_example
  exit 1
fi
if [ ${#TOKEN_KEY} -lt 32 ]; then
  echo "ERP_TOKEN_KEY 长度 ${#TOKEN_KEY} 字节,不足 32(须为 32 字节标准 base64,即 44 字符)"
  fix_example
  exit 1
fi
ok "ERP_TOKEN_KEY 已就绪"

if [ ! -f "$LOCAL_PROPERTIES" ]; then
  echo "⚠️  项目根无 local.properties(密钥来自环境变量)。MySQL/Redis 连接仍需读取,"
  echo "    建议复制 local.properties.example 为 local.properties 并填写连接信息"
fi

MYSQL_HOST=$(prop MYSQL_HOST);       MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
MYSQL_PORT=$(prop MYSQL_PORT);       MYSQL_PORT=${MYSQL_PORT:-3306}
REDIS_HOST=$(prop REDIS_HOST);       REDIS_HOST=${REDIS_HOST:-127.0.0.1}
REDIS_PORT=$(prop REDIS_PORT);       REDIS_PORT=${REDIS_PORT:-6379}

probe_port "$MYSQL_HOST" "$MYSQL_PORT" \
  && ok "MySQL 可达 $MYSQL_HOST:$MYSQL_PORT" \
  || fail "MySQL $MYSQL_HOST:$MYSQL_PORT 连不上——请检查服务已启动、local.properties 的 MYSQL_HOST/MYSQL_PORT"
probe_port "$REDIS_HOST" "$REDIS_PORT" \
  && ok "Redis 可达 $REDIS_HOST:$REDIS_PORT" \
  || fail "Redis $REDIS_HOST:$REDIS_PORT 连不上——请检查服务已启动、local.properties 的 REDIS_HOST/REDIS_PORT"

# ---------- 2. 建库(幂等:01_schema_init.sql 全 CREATE IF NOT EXISTS + INSERT IGNORE)----------

echo ""
echo "== 2/4 建库 =="
if [ "$SKIP_DB" -eq 1 ]; then
  echo "--skip-db,跳过"
else
  command -v mysql >/dev/null 2>&1 || fail "未找到 mysql 客户端——请安装 MySQL 客户端(或用 docker compose 方案,见 README),或 --skip-db 跳过本步"
  [ -f "$SCHEMA_SQL" ] || fail "建库正本缺失: $SCHEMA_SQL"
  MYSQL_USERNAME=$(prop MYSQL_USERNAME); MYSQL_USERNAME=${MYSQL_USERNAME:-root}
  MYSQL_PASSWORD=$(prop MYSQL_PASSWORD)
  echo "执行 $SCHEMA_SQL → $MYSQL_HOST:$MYSQL_USERNAME@$MYSQL_PORT(幂等,可重复执行)"
  # --default-character-set=utf8mb4 防客户端默认 latin1 把中文 COMMENT 打碎;密码经环境变量传入不进 ps
  MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 \
    -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USERNAME" < "$SCHEMA_SQL" \
    || fail "建库失败——请核对 local.properties 的 MYSQL_USERNAME/MYSQL_PASSWORD,以及账号是否有建库权限"
  ok "建库完成"
fi

# ---------- 3. 构建 ----------

echo ""
echo "== 3/4 构建 =="
if [ "$SKIP_BUILD" -eq 1 ] && [ -f "$JAR" ]; then
  echo "--skip-build 且已有 jar,跳过"
elif [ "$SKIP_BUILD" -eq 1 ]; then
  fail "--skip-build 但 $JAR 不存在,先去掉 --skip-build 跑一次完整构建"
else
  scripts/mvn-quiet.sh -pl erp-api -am -DskipTests package || fail "构建失败,全量日志见 target/mvn-quiet-*.log"
  [ -f "$JAR" ] || fail "构建通过但产物缺失: $JAR"
  ok "构建完成"
fi

# ---------- 4. 启动 ----------

echo ""
echo "== 4/4 启动 =="
if [ "$WITH_WEB" -eq 1 ]; then
  if [ ! -d "$ROOT_DIR/erp-web/node_modules" ]; then
    echo "erp-web 依赖未装,先 pnpm install..."
    (cd erp-web && pnpm install) || fail "pnpm install 失败"
  fi
  (cd erp-web && pnpm dev > "$ROOT_DIR/target/web-dev.log" 2>&1) &
  echo "前端后台启动中(:5173,日志 target/web-dev.log)"
  sleep 2
fi

echo "后端前台启动: java -jar erp-api/target/erp-api-0.1.0-SNAPSHOT.jar(:8088,Ctrl+C 停止)"
echo "启动成功后:前端 http://localhost:5173(需 --with-web 或另起 pnpm dev),默认账号 admin / admin@123"
echo ""
exec java -jar "$JAR"
