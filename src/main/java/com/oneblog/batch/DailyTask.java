package com.oneblog.batch;

import java.time.LocalDateTime;

/**
 * 매일 04:00 배치의 한 단계 (BLG-09, 4.5, D-83). 기능마다 빈을 하나씩 두면 DailyBatch가 order 순서로 돌린다.
 * 단계 하나가 실패해도 다음 단계는 돈다. 각 단계는 자기 트랜잭션을 가진다.
 */
public interface DailyTask {

    /** 낮은 수가 먼저. 폐쇄(10) → 알림(20) → 30일 삭제(30~) → 정리(90~). */
    int order();

    String name();

    /** 처리한 건수. now는 서버 시간대. */
    int run(LocalDateTime now);
}
