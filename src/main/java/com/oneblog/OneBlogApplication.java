package com.oneblog;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * One Blog 서버의 시작점. 스프링 부트를 띄우고 설정값(@ConfigurationProperties)과 비동기 작업(메일 발송)을 켠다.
 * 서버 시간대는 어디서 돌든 한국 시간으로 고정한다 (D-117). 클라우드 서버의 기본 시간대는 UTC라서,
 * 고정하지 않으면 개발 PC(한국 시간)에서 쓴 시각과 9시간 어긋난다. DB 세션 시간대는 application.yml에서 맞춘다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class OneBlogApplication {

    static final String TIME_ZONE = "Asia/Seoul";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(TIME_ZONE));
        SpringApplication.run(OneBlogApplication.class, args);
    }
}
