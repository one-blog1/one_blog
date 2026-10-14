#!/usr/bin/env bash
# 서버에서 도는 배포 스크립트 (D-118, D-135 무중단 배포). GitHub Actions(deploy.yml)가 SSH로 접속해 실행한다.
# 쓰는 법: bash ~/one-blog/deploy.sh <이미지 태그>
#   같은 폴더에 Actions가 올려 둔 파일이 있어야 한다
#   - one-blog-image.tar.gz : docker save로 만든 앱 이미지
#   - nginx-image.tar.gz    : 프록시 이미지(서버에 없을 때만 불러옴)
#   - deploy.env            : DB 접속 정보 등 (GitHub Secrets에서 만든 값, 이 스크립트가 600으로 잠근다)
# 구조 (블루-그린): 바깥 nginx → 8430 프록시(nginx 컨테이너 one-blog-proxy, 늘 켜져 있음)
#                   → 앱 one-blog-blue(8431) 또는 one-blog-green(8432) 중 지금 켜진 쪽
# 하는 일
#   1) 이미지를 불러온다 (docker load)
#   2) 처음 배포라면 JWT_SECRET·CODE_PEPPER를 만들어 secrets.env에 저장한다. 이후에는 같은 값을 계속 쓴다(바꾸면 모든 로그인이 풀림)
#   3) 지금 쉬는 색(블루·그린)으로 새 컨테이너를 띄운다. 그동안 지금 버전이 계속 요청을 받는다
#   4) 새 컨테이너의 /api/health 가 200이 될 때까지 기다린다. 실패하면 새 것만 치우고 실패로 끝낸다(지금 버전은 그대로)
#   5) 프록시가 새 포트로 보내도록 바꾸고 다시 읽는다(nginx -s reload, 끊기는 요청 없음)
#   6) 옛 컨테이너를 정상 종료한다(처리 중인 요청을 마치도록 30초까지)
#   7) 지금 것과 바로 전 것만 남기고 옛 이미지를 지운다
# 처음 한 번: 8430을 쓰던 예전 단일 컨테이너(one-blog)를 내리고 프록시를 띄울 때만 몇 초 멈춘다
set -euo pipefail

TAG="${1:?이미지 태그를 넘겨 주세요 (예: bash deploy.sh abc1234)}"
APP=one-blog
PROXY="$APP-proxy"
PROXY_IMAGE="${PROXY_IMAGE:-nginx:1.27-alpine}"
# 이 서버에서 이 앱이 쓰는 포트 (배정 8430~8439). 바깥 nginx는 PROXY_PORT로 보낸다
# 한 서버 안에서 앱을 색마다 2개씩 띄워 프록시가 번갈아 보낸다 (D-135 무중단 배포의 확장).
# 평소엔 한 색 2개가 서비스하고, 배포 때 쉬는 색 2개에 새 버전을 띄워 통째로 전환한다(블루-그린 유지).
PROXY_PORT="${APP_PORT:-8430}"
BLUE_PORTS=(${BLUE_PORTS:-8431 8432})
GREEN_PORTS=(${GREEN_PORTS:-8433 8434})
DIR="$(cd "$(dirname "$0")" && pwd)"
IMAGE="$APP:$TAG"
WAIT_SECONDS="${WAIT_SECONDS:-180}"

log() { echo "[deploy $(date '+%H:%M:%S')] $*"; }
# 실패 이유는 GitHub Actions 화면의 오류 요약(annotation)에도 보이게 한다
fail() { echo "::error title=배포 실패::$*"; log "$*"; exit 1; }

# docker 권한: 배포 계정이 docker 그룹이면 그대로, 아니면 비밀번호 없는 sudo
if docker info > /dev/null 2>&1; then
  DOCKER=(docker)
elif sudo -n docker info > /dev/null 2>&1; then
  DOCKER=(sudo -n docker)
else
  fail "docker를 실행할 수 없습니다. 서버에 Docker를 설치하고 배포 계정을 docker 그룹에 넣어 주세요: sudo usermod -aG docker \$USER"
fi

command -v curl > /dev/null || fail "curl이 없습니다. 서버에 설치해 주세요 (예: sudo apt-get install -y curl)"

# 이미지는 GitHub Actions(x86_64)에서 만든다. ARM 서버에서는 실행되지 않는다
case "$(uname -m)" in
  x86_64 | amd64) ;;
  *) fail "서버 CPU가 $(uname -m)입니다. 이미지는 x86_64용이라 실행할 수 없습니다. deploy.yml의 docker build에 --platform을 맞춰 주세요." ;;
esac

[ -f "$DIR/deploy.env" ] || fail "$DIR/deploy.env가 없습니다."
chmod 600 "$DIR/deploy.env"

# 1) 이미지 불러오기
log "이미지 불러오는 중: $IMAGE"
gunzip -c "$DIR/one-blog-image.tar.gz" | "${DOCKER[@]}" load > /dev/null
rm -f "$DIR/one-blog-image.tar.gz"
if ! "${DOCKER[@]}" image inspect "$PROXY_IMAGE" > /dev/null 2>&1; then
  if [ -f "$DIR/nginx-image.tar.gz" ]; then
    log "프록시 이미지 불러오는 중: $PROXY_IMAGE"
    gunzip -c "$DIR/nginx-image.tar.gz" | "${DOCKER[@]}" load > /dev/null
  else
    "${DOCKER[@]}" pull "$PROXY_IMAGE" > /dev/null || fail "프록시 이미지 $PROXY_IMAGE 가 없습니다."
  fi
fi
rm -f "$DIR/nginx-image.tar.gz"

# 2) 서버에서 한 번 만들고 계속 쓰는 비밀값
SECRETS="$DIR/secrets.env"
if [ ! -f "$SECRETS" ]; then
  log "처음 배포: JWT_SECRET, CODE_PEPPER를 만들어 $SECRETS 에 저장합니다"
  umask 077
  {
    echo "JWT_SECRET=$(head -c 48 /dev/urandom | base64 | tr -d '\n')"
    echo "CODE_PEPPER=$(head -c 48 /dev/urandom | base64 | tr -d '\n')"
  } > "$SECRETS"
fi
chmod 600 "$SECRETS"

# 호스트 네트워크로 띄운다: 컨테이너가 서버와 같은 네트워크·DNS를 쓴다.
# Docker 기본(bridge) 네트워크에서는 서버에서 닿는 내부망 DB(예: s4.java21.net → 10.x)에
# "No route to host"로 닿지 않았다 (서버 DNS·라우팅·방화벽을 컨테이너가 그대로 못 씀, D-118).
# 프록시는 PROXY_PORT(8430), 앱은 색마다 2개씩 BLUE_PORTS(8431·8432)·GREEN_PORTS(8433·8434)로 직접 받는다

# 컨테이너에 넘길 환경변수 = secrets.env + deploy.env. 같은 이름이 둘 다 있으면 deploy.env(GitHub Secrets) 값을 쓴다
RUN_ENV="$DIR/run.env"
umask 077
awk -F= 'NR == FNR { if ($1 != "") keep[$1] = 1; next } !($1 in keep)' "$DIR/deploy.env" "$SECRETS" > "$RUN_ENV"
cat "$DIR/deploy.env" >> "$RUN_ENV"
chmod 600 "$RUN_ENV"
for name in $(grep -oE '^(CODE_PEPPER|PRIVACY_HASH_PEPPER|JWT_SECRET)=' "$DIR/deploy.env" | tr -d '='); do
  log "$name: GitHub Secrets 값을 씁니다 (secrets.env 값 대신)"
done

run_container() {
  local name="$1" image="$2" port="$3"
  "${DOCKER[@]}" run -d \
    --name "$name" \
    --restart unless-stopped \
    --network host \
    --env-file "$RUN_ENV" \
    -e PORT="$port" \
    --memory="${APP_MEMORY:-1536m}" \
    -v one-blog-uploads:/app/uploads \
    --log-opt max-size=20m --log-opt max-file=5 \
    "$image" > /dev/null
}

running() {
  [ "$("${DOCKER[@]}" inspect -f '{{.State.Running}}' "$1" 2>/dev/null || echo false)" = "true" ]
}

health() {
  [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$1/api/health" || true)" = "200" ]
}

wait_healthy() {
  local name="$1" port="$2" i
  for ((i = 0; i < WAIT_SECONDS; i += 3)); do
    if health "$port"; then
      log "상태 확인 통과: $name (${i}초)"
      return 0
    fi
    # 컨테이너가 이미 죽었으면 더 기다리지 않는다
    if ! running "$name"; then
      log "컨테이너가 멈췄습니다: $name"
      return 1
    fi
    sleep 3
  done
  log "${WAIT_SECONDS}초 안에 상태 확인을 통과하지 못했습니다: $name"
  return 1
}

# 프록시 설정: 바깥 nginx가 보낸 요청을 지금 켜진 색의 앱 포트들로 번갈아 보낸다. upstream.conf를 바꿔 색을 전환한다
PROXY_DIR="$DIR/proxy"
mkdir -p "$PROXY_DIR"
cat > "$PROXY_DIR/nginx.conf" <<NGINX
worker_processes 1;
error_log /dev/stderr warn;
pid /tmp/nginx.pid;
events { worker_connections 1024; }
http {
  access_log off;
  # 글 이미지 묶음 10MB(application.yml multipart) + 여유
  client_max_body_size 12m;
  map \$http_x_forwarded_proto \$forwarded_proto { default \$http_x_forwarded_proto; "" \$scheme; }
  upstream one_blog {
    include /etc/nginx/one-blog/upstream.conf;
    keepalive 16;
  }
  server {
    listen $PROXY_PORT;
    location / {
      proxy_pass http://one_blog;
      proxy_http_version 1.1;
      proxy_set_header Connection "";
      proxy_set_header Host \$http_host;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$forwarded_proto;
      proxy_read_timeout 60s;
      # 앱 2개 중 하나가 죽거나 교체 중이면 나머지로 다시 보낸다 (한 서버 내 앱 다중화)
      proxy_connect_timeout 2s;
      proxy_next_upstream error timeout http_502 http_503;
      proxy_next_upstream_tries 2;
    }
  }
}
NGINX

write_upstream() {
  # 넘겨받은 포트들을 한 줄씩 적어 upstream 목록을 만든다. 하나가 죽으면 10초간 빼고 나머지로 보낸다.
  # 같은 폴더 안에서 이름만 바꿔 한 번에 바꾼다 (반쯤 쓴 파일을 nginx가 읽지 않게)
  : > "$PROXY_DIR/upstream.conf.new"
  local p
  for p in "$@"; do
    echo "server 127.0.0.1:$p max_fails=1 fail_timeout=10s;" >> "$PROXY_DIR/upstream.conf.new"
  done
  mv -f "$PROXY_DIR/upstream.conf.new" "$PROXY_DIR/upstream.conf"
}

start_proxy() {
  "${DOCKER[@]}" rm -f "$PROXY" > /dev/null 2>&1 || true
  "${DOCKER[@]}" run -d \
    --name "$PROXY" \
    --restart unless-stopped \
    --network host \
    -v "$PROXY_DIR/nginx.conf:/etc/nginx/nginx.conf:ro" \
    -v "$PROXY_DIR:/etc/nginx/one-blog:ro" \
    --log-opt max-size=10m --log-opt max-file=3 \
    "$PROXY_IMAGE" > /dev/null
}

# 3) 지금 켜진 색과 새로 띄울 색. 색마다 컨테이너 2개(-1, -2)를 띄운다
BLUE_NAMES=("$APP-blue-1" "$APP-blue-2")
GREEN_NAMES=("$APP-green-1" "$APP-green-2")

color_running() {   # 넘겨받은 이름 중 하나라도 돌고 있으면 참
  local c
  for c in "$@"; do running "$c" && return 0; done
  return 1
}

# 레거시: 블루-그린 1개 시절(one-blog-blue/green, 8431·8432)과 그 이전(one-blog, 8430 직접).
# 이들이 돌고 있으면 포트가 겹치지 않는 반대 색으로 띄운 뒤, 전환이 끝나면 함께 내린다
if color_running "${BLUE_NAMES[@]}" || running "$APP-blue"; then
  ACTIVE_COLOR=blue; NEXT_NAMES=("${GREEN_NAMES[@]}"); NEXT_PORTS=("${GREEN_PORTS[@]}")
elif color_running "${GREEN_NAMES[@]}" || running "$APP-green"; then
  ACTIVE_COLOR=green; NEXT_NAMES=("${BLUE_NAMES[@]}"); NEXT_PORTS=("${BLUE_PORTS[@]}")
else
  ACTIVE_COLOR=""; NEXT_NAMES=("${BLUE_NAMES[@]}"); NEXT_PORTS=("${BLUE_PORTS[@]}")
fi

if [ "$ACTIVE_COLOR" = blue ]; then
  ACTIVE_NAMES=("${BLUE_NAMES[@]}")
elif [ "$ACTIVE_COLOR" = green ]; then
  ACTIVE_NAMES=("${GREEN_NAMES[@]}")
else
  ACTIVE_NAMES=()
fi

LEGACY=()
for c in "$APP" "$APP-blue" "$APP-green"; do
  "${DOCKER[@]}" inspect "$c" > /dev/null 2>&1 && LEGACY+=("$c")
done

PREVIOUS_IMAGE=""
for c in "${ACTIVE_NAMES[@]}" "${LEGACY[@]}"; do
  if "${DOCKER[@]}" inspect "$c" > /dev/null 2>&1; then
    PREVIOUS_IMAGE="$("${DOCKER[@]}" inspect -f '{{.Config.Image}}' "$c" 2>/dev/null || true)"
    break
  fi
done

for n in "${NEXT_NAMES[@]}"; do
  "${DOCKER[@]}" rm -f "$n" "$n-failed" > /dev/null 2>&1 || true
done
log "새 컨테이너 실행: ${NEXT_NAMES[*]} ($IMAGE, 포트 ${NEXT_PORTS[*]}). 지금 버전(${ACTIVE_COLOR:-없음})은 그대로 요청을 받습니다"
for i in 0 1; do
  run_container "${NEXT_NAMES[$i]}" "$IMAGE" "${NEXT_PORTS[$i]}"
done

# 4) 상태 확인: 2개 다 통과해야 한다. 실패하면 새로 띄운 것만 치운다 (지금 버전은 건드리지 않았다)
NEXT_OK=true
for i in 0 1; do
  wait_healthy "${NEXT_NAMES[$i]}" "${NEXT_PORTS[$i]}" || { NEXT_OK=false; break; }
done
if [ "$NEXT_OK" != true ]; then
  CAUSE=""
  for n in "${NEXT_NAMES[@]}"; do
    running "$n" || {
      log "새 버전 로그 ($n, 마지막 80줄):"
      "${DOCKER[@]}" logs --tail 80 "$n" 2>&1 || true
    }
  done
  for n in "${NEXT_NAMES[@]}"; do
    "${DOCKER[@]}" stop -t 5 "$n" > /dev/null 2>&1 || true
    "${DOCKER[@]}" rename "$n" "$n-failed" > /dev/null 2>&1 || "${DOCKER[@]}" rm -f "$n" > /dev/null 2>&1 || true
    # 시작 실패의 핵심 줄(예외 이름·원인)만 오류 요약에 올린다
    [ -z "$CAUSE" ] && CAUSE="$("${DOCKER[@]}" logs "$n-failed" 2>&1 | grep -E "APPLICATION FAILED|Caused by|Exception:|Description:|ERROR" | grep -viE "password|secret" | tail -3 | tr '\n' ' ' | cut -c1-600 || true)"
  done
  fail "새 버전이 상태 확인을 통과하지 못해 지금 버전을 그대로 둡니다. ${CAUSE:-서버에서 docker logs ${NEXT_NAMES[0]}-failed 로 확인해 주세요.}"
fi

# 5) 프록시 전환: upstream을 새 색의 포트 2개로 통째로 바꾼다
write_upstream "${NEXT_PORTS[@]}"
if running "$PROXY"; then
  "${DOCKER[@]}" exec "$PROXY" nginx -t > /dev/null 2>&1 || fail "프록시 설정 검사에 실패했습니다. 서버에서 docker exec $PROXY nginx -t 로 확인해 주세요."
  "${DOCKER[@]}" exec "$PROXY" nginx -s reload
  log "프록시 전환: $PROXY_PORT → ${NEXT_PORTS[*]}"
else
  # 처음 한 번: 8430을 쓰던 예전 컨테이너를 내리고 그 자리에 프록시를 띄운다 (이때만 몇 초 멈춤)
  if [ "${#LEGACY[@]}" -gt 0 ]; then
    log "예전 컨테이너(${LEGACY[*]})를 내리고 $PROXY_PORT 에 프록시를 띄웁니다"
    for c in "${LEGACY[@]}"; do "${DOCKER[@]}" stop -t 30 "$c" > /dev/null || true; done
  fi
  start_proxy
  ok=false
  for ((i = 0; i < 30; i++)); do
    if health "$PROXY_PORT"; then ok=true; break; fi
    sleep 1
  done
  if [ "$ok" != true ]; then
    "${DOCKER[@]}" logs --tail 30 "$PROXY" 2>&1 || true
    "${DOCKER[@]}" rm -f "$PROXY" > /dev/null 2>&1 || true
    for c in "${LEGACY[@]}"; do "${DOCKER[@]}" start "$c" > /dev/null || true; done
    for n in "${NEXT_NAMES[@]}"; do "${DOCKER[@]}" rm -f "$n" > /dev/null 2>&1 || true; done
    fail "프록시를 띄우지 못해 예전 컨테이너로 되돌렸습니다. 서버에서 $PROXY_PORT 포트를 다른 프로그램이 쓰는지 확인해 주세요."
  fi
  log "프록시 실행: $PROXY_PORT → ${NEXT_PORTS[*]}"
fi
health "$PROXY_PORT" || fail "프록시를 거친 상태 확인에 실패했습니다 ($PROXY_PORT)."

# 6) 옛 컨테이너 정리 (처리 중인 요청을 마치도록 30초까지 기다림). 지금 색의 옛 컨테이너 2개 + 레거시
for old in "${ACTIVE_NAMES[@]}" "${LEGACY[@]}"; do
  running "$old" || "${DOCKER[@]}" inspect "$old" > /dev/null 2>&1 || continue
  log "옛 컨테이너 종료: $old"
  "${DOCKER[@]}" stop -t 30 "$old" > /dev/null 2>&1 || true
  "${DOCKER[@]}" rm "$old" > /dev/null 2>&1 || true
done
for n in "${NEXT_NAMES[@]}"; do "${DOCKER[@]}" rm -f "$n-failed" > /dev/null 2>&1 || true; done

# 7) 옛 이미지 정리: 지금 것과 바로 전 것만 남긴다
"${DOCKER[@]}" images "$APP" --format '{{.Repository}}:{{.Tag}}' \
  | grep -vxF -e "$IMAGE" -e "${PREVIOUS_IMAGE:-none}" \
  | xargs -r "${DOCKER[@]}" rmi > /dev/null 2>&1 || true

log "배포 완료: $IMAGE (${NEXT_NAMES[*]}, 포트 ${NEXT_PORTS[*]}) → 프록시 http://<서버 주소>:$PROXY_PORT"
