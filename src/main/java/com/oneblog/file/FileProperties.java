package com.oneblog.file;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** application.yml의 app.file.* — 업로드 파일 저장 폴더 (D-75). */
@ConfigurationProperties(prefix = "app.file")
public record FileProperties(@DefaultValue("./uploads") String storageDir) {
}
