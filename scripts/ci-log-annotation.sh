#!/usr/bin/env bash
# CI에서 서버 실행 확인이 실패했을 때 로그의 중요한 줄을 실행 화면 Annotations에 띄운다.
# 로그·보고서 파일을 받을 수 없는 곳에서도 실패 이유가 보이게 하기 위해서다.
# 쓰는 법: ./scripts/ci-log-annotation.sh <제목> <로그 파일 또는 -(표준 입력)>
title="${1:-server}"
src="${2:--}"
lines=$(cat "$src" | grep -E 'ERROR|Caused by|Exception|APPLICATION FAILED|FAIL|Description:|Action:|정상 종료' | tail -25)
if [ -z "$lines" ]; then
  lines=$(cat "$src" | tail -25)
fi
msg=$(printf '%s' "$lines" | cut -c1-400 | sed -e 's/%/%25/g' | awk 'BEGIN{ORS="%0A"} {print}')
echo "::error title=${title}::${msg:0:6000}"
