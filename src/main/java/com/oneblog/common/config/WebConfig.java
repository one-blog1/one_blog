package com.oneblog.common.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 정적 파일의 브라우저 캐시 (4.2 성능).
 * 정해 두지 않으면 Spring Security가 모든 응답에 "Cache-Control: no-store"를 붙여, 화면을 열 때마다
 * 글꼴 조각(한글 글꼴은 글자 범위별로 수십 개)과 CSS·JS를 다시 받는다.
 * - 글꼴: 파일 이름이 버전처럼 고정이고 내용이 바뀌지 않으므로 1년, 다시 묻지도 않음(immutable)
 * - CSS·JS·이미지: 배포하면 바로 바뀌어야 하므로 저장은 하되 쓸 때마다 바뀌었는지만 확인(no-cache → 바뀌지 않았으면 304)
 * 응답에 Cache-Control이 이미 있으면 Spring Security는 덮어쓰지 않는다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/fonts/**")
                .addResourceLocations("classpath:/static/fonts/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        for (String dir : new String[] {"css", "js", "images"}) {
            registry.addResourceHandler("/" + dir + "/**")
                    .addResourceLocations("classpath:/static/" + dir + "/")
                    .setCacheControl(CacheControl.noCache());
        }
    }
}
