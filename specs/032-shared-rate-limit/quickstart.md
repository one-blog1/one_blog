# Quickstart: 요청 제한 기록 공유 확인

## 자동 (CI)

- `unit`: `RateLimiterTest` — 꺼져 있으면 검사하지 않는다.
- `full`: `RateLimiterIntegrationTest`(MySQL)
  1. 같은 DB를 쓰는 `RateLimiter` 2개(앱 2개 흉내)에 같은 IP로 번갈아 30번 → 31번째는 어느 쪽이든 429.
  2. 다른 IP·다른 제한 이름은 따로 센다.
  3. 기록 시각을 창 밖으로 옮기면 다시 통과.
  4. DB에는 IP 원문이 없고 64자 해시만 있다.
  5. 기록할 때 그 대상의 창 밖 기록이 지워지고, `CleanupTask`가 1시간 지난 기록을 모두 지운다.
- 기존 로그인·이메일 찾기·비밀번호 재설정 테스트가 그대로 통과(테스트는 제한을 끈 설정).

## 운영 (배포 뒤)

1. 로그인 한 번 → `SELECT action, COUNT(*) FROM rate_limit_hits GROUP BY action;`에 `login` 한 줄.
2. 다음 날 04:00 뒤 1시간 넘은 기록이 없는지(Crowfoot 수용 기준 확인).
