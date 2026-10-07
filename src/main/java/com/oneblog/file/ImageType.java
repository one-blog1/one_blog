package com.oneblog.file;

import java.util.Locale;

/** 받는 이미지 형식 (6.3). svg는 스크립트를 넣을 수 있어 받지 않는다. */
public enum ImageType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String extension;

    ImageType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String mimeType() {
        return mimeType;
    }

    /** 저장 이름에 붙일 확장자. */
    public String extension() {
        return extension;
    }

    /** 파일 이름의 확장자로 판단. 받지 않는 확장자면 null. */
    public static ImageType fromFilename(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return switch (filename.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> JPEG;
            case "png" -> PNG;
            case "gif" -> GIF;
            case "webp" -> WEBP;
            default -> null;
        };
    }

    /** 요청에 적힌 MIME 타입으로 판단. 받지 않는 타입이면 null. */
    public static ImageType fromMimeType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String value = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "image/jpeg", "image/jpg", "image/pjpeg" -> JPEG;
            case "image/png" -> PNG;
            case "image/gif" -> GIF;
            case "image/webp" -> WEBP;
            default -> null;
        };
    }
}
