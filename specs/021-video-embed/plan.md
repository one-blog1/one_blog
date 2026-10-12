# Implementation Plan: 글 안 영상 재생

**Branch**: `main` | **Date**: 2026-10-12 | **Spec**: [spec.md](spec.md)

## Summary

DB 변경 없음. 글 본문을 HTML로 바꾸는 `MarkdownRenderer` 끝에 영상 링크 문단을 재생기로 바꾸는 단계를 더한다. 사이트별 "링크 모양 → 재생기 주소" 규칙은 `VideoEmbeds` 한 곳에 둔다. 글 상세·메인 공지 모두 같은 렌더러를 쓰므로 함께 적용된다.

## Research

### R1. 아무 사이트나 되게(oEmbed) vs 사이트 목록

- **Decision**: 사이트 목록. 영상 번호만 뽑아 서버가 재생기 주소를 만든다.
- **Alternatives**: oEmbed는 상대 사이트가 준 HTML을 그대로 넣어야 해서 SEC-06 기준(서버가 거른 것만)과 맞지 않고, 서버가 바깥으로 요청해야 한다.

### R2. 재생기 주소

- 유튜브: 쿠키를 줄인 `www.youtube-nocookie.com/embed/{id}`. Referer가 없으면 재생이 막히므로 `referrerpolicy=strict-origin-when-cross-origin`.
- 치지직: 클립 `chzzk.naver.com/embed/clip/{id}`, 다시보기 `chzzk.naver.com/embed/vod/{번호}`.
- SOOP: 2026-03 도메인 변경(sooplive.co.kr → sooplive.com) 뒤 `vod.sooplive.com/player/{번호}/embed?showChat=false&autoPlay=false&mutePlay=false`.
- 근거: 네이버 카페에서 같은 사이트들을 띄우는 공개 스크립트(nomomo/Naver-Cafe-Twitch-Clip-Loader v1.5.9)와 SOOP 퍼가기 예시. 각 사이트가 바꾸면 `VideoEmbeds`만 고친다.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. 보안 기준선 | 사용자 HTML은 그대로 거르고, iframe은 서버가 허용 사이트 주소로만 만든다. sandbox로 페이지 이동·폼 전송 금지. frame-src는 세 사이트만 | ✅ |
| IV. ERD 우선 | 테이블 변경 없음 | ✅ |
| V. 단순함 | 새 라이브러리·바깥 요청 없음 | ✅ |
