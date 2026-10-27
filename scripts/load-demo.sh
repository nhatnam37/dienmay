#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
config="${1:-.env.demo}"
if [[ ! -f "$config" ]]; then echo 'Sao chép .env.demo.example thành .env.demo, đặt ba mật khẩu DB rồi chạy lại.' >&2; exit 1; fi
# Không source file env vào shell: tránh thực thi nội dung cấu hình.
grep -qx 'APP_PROFILE=demo' "$config" || { echo 'Chỉ cho phép APP_PROFILE=demo' >&2; exit 1; }
grep -Eq '^DB_NAME=[A-Za-z0-9_]+_demo$' "$config" || { echo 'DB_NAME phải kết thúc bằng _demo' >&2; exit 1; }
grep -qx 'DEMO_SEED=true' "$config" || { echo 'Cần DEMO_SEED=true' >&2; exit 1; }
docker compose --env-file "$config" up --build -d --force-recreate --wait --wait-timeout 600
