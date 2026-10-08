#!/usr/bin/env bash
# 배포 후 확인 (D-117): 서버가 떠서 요청을 제대로 받는지 본다. CI와 실제 배포 뒤에 같이 쓴다.
# 쓰는 법: ./scripts/smoke-check.sh https://내-도메인   (기본 http://127.0.0.1:8080)
# 1) /api/health 가 200이 될 때까지 최대 WAIT_SECONDS(기본 120)초 기다린다 (시작할 때 Flyway 마이그레이션이 돈다)
# 2) 첫 화면, CSS·JS, 비회원 API, 로그인 필요 API가 예상한 상태 코드로 답하는지 본다
set -u
BASE="${1:-http://127.0.0.1:8080}"
WAIT="${WAIT_SECONDS:-120}"

echo "확인할 주소: $BASE"
for ((i = 0; i < WAIT; i += 2)); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/health")" = "200" ]; then
    echo "상태 확인 통과 (${i}초)"
    break
  fi
  sleep 2
done

fail=0
check() {
  local path="$1" expected="$2" code
  code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE$path")
  if [ "$code" = "$expected" ]; then
    echo "  ok   $code $path"
  else
    echo "  FAIL $code $path (기대: $expected)"
    fail=1
  fi
}

check /api/health 200
check / 200
check /login.html 200
check /css/app.css 200
check /js/api.js 200
check "/api/feed?page=1" 200
check /api/blogs 200
check /api/notices 200
check /api/me 401

if [ "$fail" -ne 0 ]; then
  echo "확인 실패"
  exit 1
fi
echo "모두 통과"
