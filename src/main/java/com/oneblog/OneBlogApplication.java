package com.oneblog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/** One Blog 서버의 시작점. 스프링 부트를 띄우고 설정값(@ConfigurationProperties)과 비동기 작업(메일 발송)을 켠다. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class OneBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(OneBlogApplication.class, args);
    }
}
