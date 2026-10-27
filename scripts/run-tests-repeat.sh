#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
runs="${1:-5}"
if ! [[ "$runs" =~ ^[1-9][0-9]*$ ]]; then echo "Số lần chạy phải là số nguyên dương." >&2; exit 2; fi
log_dir="$(mktemp -d "${TMPDIR:-/tmp}/dienmay-repeat.XXXXXX")"
echo "Lưu log từng lần tại: $log_dir"
for ((run=1; run<=runs; run++)); do
  echo "[$run/$runs] ./mvnw clean verify"
  mkdir -p "$log_dir/run-$run"
  if ./mvnw -B clean verify > "$log_dir/run-$run/build.log" 2>&1; then
    cp -R target/surefire-reports target/failsafe-reports "$log_dir/run-$run/"
    cp target/site/jacoco/jacoco.xml "$log_dir/run-$run/jacoco.xml"
    echo "[$run/$runs] ĐẠT"
  else
    echo "[$run/$runs] KHÔNG ĐẠT — dừng ngay. Log: $log_dir/run-$run/build.log" >&2
    grep -E '^\[ERROR\]|Rule violated|<<< FAILURE' "$log_dir/run-$run/build.log" >&2 || true
    exit 1
  fi
done
echo "Hoàn tất $runs/$runs lần thành công. Log: $log_dir"
