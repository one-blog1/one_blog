#!/usr/bin/env bash
# 서버에서 도는 배포 스크립트 (D-118). GitHub Actions(deploy.yml)가 SSH로 접속해 실행한다.
# 쓰는 법: bash ~/one-blog/deploy.sh <이미지 태그>
#   같은 폴더에 Actions가 올려 둔 두 파일이 있어야 한다
#   - one-blog-image.tar.gz : docker save로 만든 이미지
#   - deploy.env            : DB 접속 정보 등 (GitHub Secrets에서 만든 값, 이 스크립트가 600으로 잠근다)
# 하는 일
#   1) 이미지를 불러온다 (docker load)
#   2) 처음 배포라면 JWT_SECRET·CODE_PEPPER를 만들어 secrets.env에 저장한다. 이후에는 같은 값을 계속 쓴다(바꾸면 모든 로그인이 풀림)
#   3) 기존 컨테이너를 정상 종료하고 새 컨테이너를 8430 포트로 띄운다
#   4) /api/health 가 200이 될 때까지 기다린다. 실패하면 바로 전 이미지로 되돌리고 실패로 끝낸다
#   5) 성공하면 지금 것과 바로 전 것만 남기고 옛 이미지를 지운다
set -euo pipefail

TAG="${1:?이미지 태그를 넘겨 주세요 (예: bash deploy.sh abc1234)}"
APP=one-blog
PORT="${APP_PORT:-8430}"
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
# 앱은 PORT(8430)로 직접 받는다. bridge로 바꾸려면 DOCKER_NETWORK=bridge
if [ "${DOCKER_NETWORK:-host}" = "bridge" ]; then
  NETWORK_ARGS=(-p "$PORT:$PORT")
else
  NETWORK_ARGS=(--network host)
fi

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
  local image="$1"
  "${DOCKER[@]}" run -d \
    --name "$APP" \
    --restart unless-stopped \
    "${NETWORK_ARGS[@]}" \
    --env-file "$RUN_ENV" \
    -e PORT="$PORT" \
    -v one-blog-uploads:/app/uploads \
    --log-opt max-size=20m --log-opt max-file=5 \
    "$image" > /dev/null
}

wait_healthy() {
  local i
  for ((i = 0; i < WAIT_SECONDS; i += 3)); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/api/health" || true)" = "200" ]; then
      log "상태 확인 통과 (${i}초)"
      return 0
    fi
    # 컨테이너가 이미 죽었으면 더 기다리지 않는다
    if [ "$("${DOCKER[@]}" inspect -f '{{.State.Running}}' "$APP" 2>/dev/null || echo false)" != "true" ]; then
      log "컨테이너가 멈췄습니다"
      return 1
    fi
    sleep 3
  done
  log "${WAIT_SECONDS}초 안에 상태 확인을 통과하지 못했습니다"
  return 1
}

# 3) 기존 컨테이너 내리기 (처리 중인 요청을 마치도록 30초까지 기다림)
PREVIOUS=""
if "${DOCKER[@]}" inspect "$APP" > /dev/null 2>&1; then
  PREVIOUS="$("${DOCKER[@]}" inspect -f '{{.Config.Image}}' "$APP")"
  log "기존 컨테이너 종료: $PREVIOUS"
  "${DOCKER[@]}" stop -t 30 "$APP" > /dev/null || true
  "${DOCKER[@]}" rm "$APP" > /dev/null
fi

log "새 컨테이너 실행: $IMAGE (포트 $PORT)"
run_container "$IMAGE"

# 4) 상태 확인. 실패하면 되돌리기
if ! wait_healthy; then
  log "새 버전 로그 (마지막 80줄):"
  "${DOCKER[@]}" logs --tail 80 "$APP" 2>&1 || true
  # 실패한 컨테이너는 이름만 바꿔 남긴다 (원인 확인용, 다음 배포 때 지움)
  "${DOCKER[@]}" rm -f "$APP-failed" > /dev/null 2>&1 || true
  "${DOCKER[@]}" stop -t 5 "$APP" > /dev/null 2>&1 || true
  "${DOCKER[@]}" rename "$APP" "$APP-failed" || "${DOCKER[@]}" rm -f "$APP" > /dev/null || true
  if [ -n "$PREVIOUS" ] && [ "$PREVIOUS" != "$IMAGE" ]; then
    log "바로 전 버전으로 되돌립니다: $PREVIOUS"
    run_container "$PREVIOUS"
    wait_healthy || log "바로 전 버전도 상태 확인을 통과하지 못했습니다. 서버에서 docker logs $APP 로 확인해 주세요."
  fi
  # 시작 실패의 핵심 줄(예외 이름·원인)만 오류 요약에 올린다
  CAUSE="$("${DOCKER[@]}" logs "$APP-failed" 2>&1 | grep -E "APPLICATION FAILED|Caused by|Exception:|Description:|ERROR" | grep -viE "password|secret" | tail -3 | tr '\n' ' ' | cut -c1-600 || true)"
  fail "새 버전이 상태 확인을 통과하지 못해 되돌렸습니다. ${CAUSE:-서버에서 docker logs로 확인해 주세요.}"
fi

# 5) 옛 이미지 정리: 지금 것과 바로 전 것만 남긴다
"${DOCKER[@]}" images "$APP" --format '{{.Repository}}:{{.Tag}}' \
  | grep -vxF -e "$IMAGE" -e "${PREVIOUS:-none}" \
  | xargs -r "${DOCKER[@]}" rmi > /dev/null 2>&1 || true

log "배포 완료: $IMAGE → http://<서버 주소>:$PORT"
