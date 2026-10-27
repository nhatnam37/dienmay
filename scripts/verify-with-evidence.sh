#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
run_id="$(date -u +%Y%m%dT%H%M%SZ)"
evidence="docs/evidence/automated/$run_id"
mkdir -p "$evidence"
started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
start_seconds=$(date +%s)
commit=$(git rev-parse HEAD)
git status --porcelain > "$evidence/working-tree.txt"
if [[ -s "$evidence/working-tree.txt" ]]; then dirty=true; else dirty=false; fi
{ java -version; sw_vers; docker version --format '{{.Client.Version}} / {{.Server.Version}}'; } > "$evidence/environment.txt" 2>&1
# Dấu vân tay source thực sự chạy, kể cả file chưa commit; không lưu mật khẩu/config môi trường.
find src/main src/test -type f -print0 | sort -z | xargs -0 shasum -a 256 > "$evidence/source.sha256"
shasum -a 256 pom.xml >> "$evidence/source.sha256"
log_file="${TMPDIR:-/tmp}/dienmay-9d-$run_id.log"
./mvnw -B clean verify > "$log_file" 2>&1
exit_code=$?
finished="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
elapsed=$(( $(date +%s) - start_seconds ))
for folder in surefire-reports failsafe-reports; do
  mkdir -p "$evidence/$folder"
  if [[ -d "target/$folder" ]]; then find "target/$folder" -name 'TEST-*.xml' -exec cp '{}' "$evidence/$folder/" \;; fi
done
if [[ -f target/site/jacoco/jacoco.xml ]]; then cp target/site/jacoco/jacoco.xml "$evidence/"; fi
grep -E '^\[INFO\] (Tests run:|BUILD |Total time:|Finished at:)|Rule violated|^\[ERROR\]' "$log_file" > "$evidence/build-summary.log" || true
{
  echo "started=$started"; echo "finished=$finished"; echo "commit=$commit"; echo "dirty=$dirty"
  echo 'command=./mvnw -B clean verify'; echo "exitCode=$exit_code"; echo "elapsedSeconds=$elapsed"
  echo "evidence=$evidence"; echo "rawLog=$log_file"
} > "$evidence/run.properties"
cp "$evidence/run.properties" docs/evidence/automated/latest-run.properties
find "$evidence" -type f ! -name evidence.sha256 -print0 | sort -z | xargs -0 shasum -a 256 > "$evidence/evidence.sha256"
echo "Kết quả: exit=$exit_code, ${elapsed}s; bằng chứng: $evidence; log đầy đủ: $log_file"
exit "$exit_code"
