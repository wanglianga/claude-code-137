#!/usr/bin/env bash
# 本地开发用 PostgreSQL 实例：不依赖系统 postgres 服务，数据放在项目 .pgdata 目录
# 用法: scripts/dev-db.sh [start|stop|reset|status]
set -euo pipefail

PG_BIN="${PG_BIN:-/usr/lib/postgresql/15/bin}"
PGDATA="${PGDATA:-$(cd "$(dirname "$0")/.." && pwd)/.pgdata}"
PGPORT="${PGPORT:-5433}"
DB_NAME="med_recycle"
DB_USER="med"
DB_PASS="med123"
LOG="$PGDATA/server.log"

cmd="${1:-start}"

start() {
  if [ ! -d "$PGDATA/base" ]; then
    echo "[dev-db] 初始化数据目录 $PGDATA (port=$PGPORT)"
    "$PG_BIN/initdb" -D "$PGDATA" -U "$DB_USER" --pwfile=<(echo "$DB_PASS") \
      --auth-local=trust --auth-host=md5 -E UTF8 --locale=C >/dev/null
    echo "port = $PGPORT" >> "$PGDATA/postgresql.conf"
    echo "listen_addresses = 'localhost'" >> "$PGDATA/postgresql.conf"
    echo "unix_socket_directories = '$PGDATA'" >> "$PGDATA/postgresql.conf"
  fi
  if "$PG_BIN/pg_ctl" -D "$PGDATA" status >/dev/null 2>&1; then
    echo "[dev-db] 已在运行"
  else
    "$PG_BIN/pg_ctl" -D "$PGDATA" -l "$LOG" -w start >/dev/null
    echo "[dev-db] 已启动 (port=$PGPORT)"
  fi
  if ! PGPASSWORD="$DB_PASS" psql -h localhost -p "$PGPORT" -U "$DB_USER" -d postgres \
      -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" | grep -q 1; then
    PGPASSWORD="$DB_PASS" psql -h localhost -p "$PGPORT" -U "$DB_USER" -d postgres \
      -c "CREATE DATABASE $DB_NAME" >/dev/null
    echo "[dev-db] 已创建数据库 $DB_NAME"
  fi
  echo "[dev-db] 连接串: jdbc:postgresql://localhost:$PGPORT/$DB_NAME  user=$DB_USER password=$DB_PASS"
}

stop() {
  "$PG_BIN/pg_ctl" -D "$PGDATA" -m fast stop && echo "[dev-db] 已停止"
}

case "$cmd" in
  start) start ;;
  stop) stop ;;
  status) "$PG_BIN/pg_ctl" -D "$PGDATA" status ;;
  reset)
    stop >/dev/null 2>&1 || true
    rm -rf "$PGDATA"
    start
    ;;
  *) echo "用法: $0 [start|stop|reset|status]"; exit 1 ;;
esac
