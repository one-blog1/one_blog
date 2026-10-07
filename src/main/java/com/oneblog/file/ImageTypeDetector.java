package com.oneblog.file;

/**
 * 파일 앞부분 바이트(시그니처)로 실제 형식을 판단한다 (6.3, SEC-08).
 * 확장자만 바꾼 실행 파일이나 svg를 막기 위해 확장자·MIME 타입과 함께 확인한다.
 */
public final class ImageTypeDetector {

    private ImageTypeDetector() {
    }

    /** 받는 형식이 아니면 null. */
    public static ImageType detect(byte[] data) {
        if (data == null) {
            return null;
        }
        if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) {
            return ImageType.JPEG;
        }
        if (startsWith(data, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return ImageType.PNG;
        }
        if (startsWith(data, 0, 'G', 'I', 'F', '8', '7', 'a') || startsWith(data, 0, 'G', 'I', 'F', '8', '9', 'a')) {
            return ImageType.GIF;
        }
        if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) {
            return ImageType.WEBP;
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
