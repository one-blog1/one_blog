# Implementation Plan: 댓글·대댓글·좋아요

**Branch**: `main` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Flyway V6로 ERD의 `comments`, `post_likes`를 추가한다. 댓글은 `parent_id`(첫 댓글)와 `reply_to_user_id`(@상대)로 1단계 대댓글을 표현하고, 목록은 글의 댓글을 한 번에 읽어 첫 댓글 아래에 답글을 묶는다. 좋아요는 회원 행을 잠근 뒤 있으면 지우고 없으면 넣으며, `posts.like_count`를 DB에서 바로 더한다. 알림(011)·차단(013)이 끼어들 자리로 `CommentListener`, `LikeListener`를 둔다.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. 역할 DB 확인 | 남의 댓글 삭제는 요청마다 `blog_members.can_manage_posts` | ✅ |
| III. textContent | 댓글·닉네임 모두 textContent | ✅ |
| IV. ERD·Flyway | V6: comments, post_likes, FK 5개, 인덱스 2개 | ✅ |
| IV. 소프트 삭제 | comments.deleted_at | ✅ |
| 권한 거부 테스트 | 비회원·관리자·남의 댓글 수정·삭제·비공개 글 | ✅ |

## API

| 메서드·주소 | 권한 | 비고 |
|---|---|---|
| `GET /api/posts/{id}/comments?key` | 글을 볼 수 있음 | 첫 댓글 목록, 각 `replies[]` |
| `POST /api/posts/{id}/comments?key` | 로그인(관리자 제외) | `{content, parentId?}` → 201 |
| `PUT /api/comments/{id}` | 작성자 | `{content}` → 204 |
| `DELETE /api/comments/{id}` | 작성자, 글 관리 권한 | 204 |
| `POST /api/posts/{id}/like?key` | 로그인(관리자 제외) | 누르기·취소 → `{liked, likeCount}` |

## Project Structure

```text
src/main/resources/db/migration/V6__create_comments_and_likes.sql
src/main/java/com/oneblog/comment/  Comment, CommentRepository, CommentService, CommentController, CommentResponse, CommentListener
src/main/java/com/oneblog/like/     PostLike, PostLikeRepository, LikeService, LikeController, LikeListener, PostLikeExtension
src/main/resources/static/js/comments.js, post.html
src/test/java/com/oneblog/comment/CommentLikeIntegrationTest.java
```
