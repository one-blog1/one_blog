package com.oneblog.batch;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 새벽 4시(한국 시간) 배치 (D-12, D-83, 4.5). zone을 꼭 적는다: AWS 서버 기본 시간대는 UTC.
 * 여러 서버 중 한 대만 돈다 (SchedulerLock, SCL-03). 밀린 날이 있어도 "시각이 지난 것"을 고르므로 다음 실행이 처리한다.
 */
@Component
public class DailyBatch {

    static final String LOCK_NAME = "daily-batch";
    private static final Logger log = LoggerFactory.getLogger(DailyBatch.class);

    private final List<DailyTask> tasks;
    private final SchedulerLock lock;

    public DailyBatch(List<DailyTask> tasks, SchedulerLock lock) {
        this.tasks = tasks.stream().sorted(Comparator.comparingInt(DailyTask::order)).toList();
        this.lock = lock;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void scheduled() {
        if (!lock.tryLock(LOCK_NAME, Duration.ofHours(1))) {
            log.info("다른 서버가 04:00 배치를 실행 중이라 건너뜁니다.");
            return;
        }
        try {
            runAll(LocalDateTime.now());
        } finally {
            lock.release(LOCK_NAME);
        }
    }

    /** 모든 단계를 순서대로. 테스트와 운영자 수동 실행도 이것을 부른다. */
    public Map<String, Integer> runAll(LocalDateTime now) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (DailyTask task : tasks) {
            try {
                int count = task.run(now);
                result.put(task.name(), count);
                log.info("04:00 배치 {}: {}건", task.name(), count);
            } catch (RuntimeException e) {
                result.put(task.name(), -1);
                log.error("04:00 배치 {} 실패", task.name(), e);
            }
        }
        return result;
    }
}
