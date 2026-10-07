package com.oneblog.file;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

/** 테스트용 작은 이미지. WebP는 ImageIO가 만들지 못해 덩어리 구조만 맞춘 바이트를 쓴다. */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png() {
        return write("png");
    }

    public static byte[] jpeg() {
        return write("jpg");
    }

    public static byte[] gif() {
        return write("gif");
    }

    /** RIFF/WEBP 머리 + VP8L 덩어리 하나 (형식 판단·덩어리 처리용). */
    public static byte[] webp() {
        byte[] payload = {0x2F, 0x00, 0x00, 0x00, 0x00};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        int riffSize = 4 + 8 + payload.length + (payload.length & 1);
        out.writeBytes(le32(riffSize));
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes("VP8L".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le32(payload.length));
        out.writeBytes(payload);
        if ((payload.length & 1) == 1) {
            out.write(0);
        }
        return out.toByteArray();
    }

    /** 3MB를 조금 넘는 PNG 모양의 바이트 (크기 검사용). */
    public static byte[] oversizedPng() {
        byte[] small = png();
        byte[] big = new byte[3 * 1024 * 1024 + 1];
        System.arraycopy(small, 0, big, 0, small.length);
        return big;
    }

    static byte[] le32(int v) {
        return new byte[] {(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    private static byte[] write(String format) {
        int imageType = "gif".equals(format) ? BufferedImage.TYPE_BYTE_INDEXED : BufferedImage.TYPE_INT_RGB;
        BufferedImage image = new BufferedImage(4, 3, imageType);
        image.setRGB(1, 1, 0x2563EB);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("이미지를 만들지 못했습니다: " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
