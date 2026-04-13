#!/bin/bash
set -euo pipefail

# --- CivicWorks Broad Test Runner (Dockerized) ---
# Runs the full test suite inside a containerized JDK 21 using
# docker-compose.test.yml. The host only needs Docker + curl; no Java or
# Gradle installation is required.
#
# On failure, exits non-zero. Always tears down its own test containers
# regardless of success/failure.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

PROJECT="civicworks_tests_$$"
COMPOSE=(docker compose -p "$PROJECT" -f docker-compose.test.yml)

# Preflight
if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is required on PATH to run ./run_tests.sh" >&2
  exit 2
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: 'docker compose' plugin is required (Compose v2)" >&2
  exit 2
fi

cleanup() {
  local ec=$?
  echo ""
  echo "=== Tearing down test containers ==="
  "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  exit "$ec"
}
trap cleanup EXIT

echo "=== CivicWorks Test Suite (Dockerized) ==="
echo "Project: $PROJECT"
echo "Running all tests inside the prebaked test runner image ..."
echo "(first run will build the image and pre-fetch dependencies; subsequent runs reuse the gradle-cache volume)"
echo ""

# Build the runner image once, retrying once on transient registry/network errors.
if ! "${COMPOSE[@]}" build tests; then
  echo "build failed, retrying once after 5s..." >&2
  sleep 5
  "${COMPOSE[@]}" build tests
fi

"${COMPOSE[@]}" run --rm tests

echo ""
echo "=== Test run complete ==="
echo "Reports: build/reports/tests/test/index.html"
