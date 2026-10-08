package com.oneblog.batch;

import java.time.LocalDateTime;
import java.util.function.ToIntFunction;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.oneblog.blog.ops.BlogCloseService;
import com.oneblog.blog.ops.BlogTransferService;

/** 04:00 배치의 블로그 단계: 예정 시각이 지난 폐쇄(BLG-09), 위임 요청 자동 취소(BLG-08), 폐쇄 3일·1일 전 알림. */
@Configuration
public class BlogLifecycleTasks {

    @Bean
    DailyTask closeDueBlogs(BlogCloseService closeService) {
        return task(10, "블로그 폐쇄", closeService::closeDue);
    }

    @Bean
    DailyTask expireTransfers(BlogTransferService transferService) {
        return task(15, "위임 요청 자동 취소", transferService::expire);
    }

    @Bean
    DailyTask remindClosing(BlogCloseService closeService) {
        return task(20, "폐쇄 예정 알림", closeService::remind);
    }

    static DailyTask task(int order, String name, ToIntFunction<LocalDateTime> body) {
        return new DailyTask() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public int run(LocalDateTime now) {
                return body.applyAsInt(now);
            }
        };
    }
}
