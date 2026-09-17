#!/usr/bin/env bash
# CI 的临时 Postgres 已由工作流建好，Alembic / 种子也已完成。
# Java 启动时在同一个库上跑 Flyway 增量；两种 DATABASE_URL 格式不能混用。
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."
: "${PARITY_PYTHON_DATABASE_URL:?must point to the disposable parity database}"
: "${PARITY_JAVA_DATABASE_URL:?must point to the same disposable parity database}"

log_dir="${PARITY_LOG_DIR:-$PWD/.local/ci-parity-logs}"
mkdir -p "$log_dir"
python_port="${PARITY_PYTHON_PORT:-18000}"
java_port="${PARITY_JAVA_PORT:-18080}"
python_pid=""
java_pid=""

cleanup() {
  local pid
  for pid in "$python_pid" "$java_pid"; do
    if [[ -n "$pid" ]]; then kill "$pid" 2>/dev/null || true; fi
  done
  # 有界关闭：即使应用的 graceful shutdown 卡住，也不能让 CI 挂到 job 超时。
  for ((attempt = 0; attempt < 10; attempt++)); do
    if ! kill -0 "$python_pid" 2>/dev/null && ! kill -0 "$java_pid" 2>/dev/null; then break; fi
    sleep 1
  done
  for pid in "$python_pid" "$java_pid"; do
    if [[ -n "$pid" ]]; then
      kill -KILL "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    fi
  done
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

shopt -s nullglob
jars=(backend-java/build/libs/*.jar)
boot_jars=()
for jar in "${jars[@]}"; do
  [[ "$jar" == *-plain.jar ]] || boot_jars+=("$jar")
done
if [[ ${#boot_jars[@]} -ne 1 ]]; then
  echo "Expected exactly one bootJar; run ./gradlew bootJar first." >&2
  exit 1
fi

(
  cd backend
  exec env DATABASE_URL="$PARITY_PYTHON_DATABASE_URL" \
    .venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port "$python_port"
) >"$log_dir/python.log" 2>&1 &
python_pid=$!

env DATABASE_URL="$PARITY_JAVA_DATABASE_URL" SERVER_PORT="$java_port" \
  java -jar "${boot_jars[0]}" >"$log_dir/java.log" 2>&1 &
java_pid=$!

wait_ready() {
  local name="$1" pid="$2" port="$3" deadline=$((SECONDS + 180))
  while ((SECONDS < deadline)); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "$name exited before readiness; see $log_dir/$name.log" >&2
      tail -n 80 "$log_dir/$name.log" >&2
      return 1
    fi
    if curl --fail --silent --max-time 2 "http://127.0.0.1:$port/api/v1/health" >/dev/null; then
      echo "$name ready on $port"
      return 0
    fi
    sleep 1
  done
  echo "$name readiness timed out; see $log_dir/$name.log" >&2
  tail -n 80 "$log_dir/$name.log" >&2
  return 1
}

wait_ready python "$python_pid" "$python_port"
wait_ready java "$java_pid" "$java_port"
# urllib 的既有对照客户端没有请求超时；为整轮加上界，保留退出码并触发清理。
timeout 180s backend/.venv/bin/python tools/parity/check.py \
  --python "http://127.0.0.1:$python_port" \
  --java "http://127.0.0.1:$java_port" --verbose 2>&1 | tee "$log_dir/parity.log"
