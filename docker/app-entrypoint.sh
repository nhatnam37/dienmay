#!/bin/sh
set -eu
case "${SPRING_PROFILES_ACTIVE:-prod}" in
  prod|demo) ;;
  *) echo 'Image triển khai chỉ nhận prod hoặc demo; dev/test chạy bằng Maven.' >&2; exit 1 ;;
esac
case "${UPLOAD_DIR:-/app/uploads}" in
  /app/uploads|/app/uploads/*) ;;
  *) echo 'UPLOAD_DIR trong Docker phải nằm trong /app/uploads để được volume bảo vệ.' >&2; exit 1 ;;
esac
mkdir -p "${UPLOAD_DIR:-/app/uploads}"
exec java -jar /app/app.jar
