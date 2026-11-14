#!/usr/bin/env bash
# E2E_SCRIPT=../load/simulated-pilot.mjs runs the simulated pilot instead of the browser test.
# Starts a throwaway PostgreSQL and a fresh seeded Navya from target/navya.jar, runs the browser test, stops both.
# Needs: java 21, node, Chrome or Chromium (set CHROME if it is not on PATH as google-chrome).
# Build first: ./mvnw -DskipTests package test-compile
set -euo pipefail
cd "$(dirname "$0")/.."
JAR=target/navya.jar
[ -f "$JAR" ] || { echo "Build first: ./mvnw -DskipTests package test-compile" >&2; exit 2; }
WORK=$(mktemp -d)
PORT=${PORT:-8089}
PW=${NV_PASSWORD:-$(openssl rand -hex 12)}   # random per run unless set, so no password is stored in the repo
PIDS=()
cleanup() { for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done; rm -rf "$WORK"; }
trap cleanup EXIT

if [ -z "${DB_URL:-}" ]; then
  ./mvnw -B -q dependency:build-classpath -Dmdep.outputFile="$WORK/cp.txt" -Dmdep.includeScope=test >/dev/null
  java -cp "target/test-classes:target/classes:$(cat "$WORK/cp.txt")" com.navya.DevPostgres 5439 > "$WORK/pg.log" 2>&1 &
  PIDS+=($!)
  for _ in $(seq 1 40); do grep -q '^DB_URL=' "$WORK/pg.log" && break; sleep 0.5; done
  DB_URL=$(grep '^DB_URL=' "$WORK/pg.log" | cut -d= -f2-)
fi

DEV=true DB_URL="$DB_URL" DATA_DIR="$WORK/data" PORT="$PORT" SEED=true SEED_PASSWORD="$PW" java -jar "$JAR" > "$WORK/app.log" 2>&1 &
PIDS+=($!)
for _ in $(seq 1 60); do curl -sf "http://localhost:$PORT/health" >/dev/null && break; sleep 0.5; done
cd e2e
[ -d node_modules ] || npm install --no-audit --no-fund
BASE_URL="http://localhost:$PORT" NV_PASSWORD="$PW" CHROME="${CHROME:-$(command -v google-chrome || command -v chromium)}" node "${E2E_SCRIPT:-ui-smoke.mjs}"
