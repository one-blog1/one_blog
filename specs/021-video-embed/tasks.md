# Tasks: 글 안 영상 재생

- [X] T001 사이트별 재생기 주소 확인 (유튜브 nocookie, 치지직 clip·vod, SOOP 새 도메인)
- [X] T002 `common/text/VideoEmbeds.java` 링크 규칙과 재생기 주소, `FRAME_ORIGINS`
- [X] T003 `MarkdownRenderer` 거른 뒤 한 줄짜리 영상 링크 문단 → iframe(sandbox, lazy)
- [X] T004 `SecurityConfig` frame-src에 세 사이트
- [X] T005 `app.css` 글(004) 자리에 `.video-embed` 16:9
- [X] T006 `privacy.html` 9번에 외부 영상 재생 안내
- [X] T007 테스트 `VideoEmbedTest`(지원 링크, 문장 중간·다른 글자 링크, 허용 안 한 주소, 직접 쓴 iframe 제거, 미리보기 글자), `VideoFrameCspTest`
- [ ] T008 CI `./gradlew test` 통과
- [ ] T009 배포 뒤 세 사이트 실제 링크로 재생 확인
- [ ] T010 기능을 마치면 이 폴더를 문서 저장소 `specs/`로 옮김
