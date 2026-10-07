package com.oneblog.file;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 사진의 위치 정보(EXIF GPS 등)를 지운다 (6.3, research R4).
 * 다시 그리지(재인코딩) 않고 메타데이터 구간만 지워 화질과 GIF 움직임을 그대로 둔다.
 *
 * - JPEG: Exif 안의 GPS 정보만 0으로 지우고(사진 방향 정보는 남김), XMP(APP1)·Photoshop(APP13) 구간은 뺀다.
 *   Exif 구조가 이상하면 Exif 구간 전체를 뺀다.
 * - PNG: eXIf, tEXt, iTXt, zTXt 덩어리를 뺀다.
 * - WebP: EXIF, XMP 덩어리를 빼고 VP8X의 표시 비트를 끈다.
 * - GIF: 위치 정보를 담지 않아 그대로 둔다.
 *
 * 구조가 깨진 파일은 IllegalArgumentException을 던진다 (받지 않는 파일로 처리).
 */
public final class ImageMetadataStripper {

    private ImageMetadataStripper() {
    }

    public static byte[] strip(ImageType type, byte[] data) {
        return switch (type) {
            case JPEG -> stripJpeg(data);
            case PNG -> stripPng(data);
            case WEBP -> stripWebp(data);
            case GIF -> data;
        };
    }

    // ── JPEG ─────────────────────────────────────────────

    private static final int SOI = 0xD8;
    private static final int EOI = 0xD9;
    private static final int SOS = 0xDA;
    private static final int APP1 = 0xE1;
    private static final int APP13 = 0xED;
    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};

    static byte[] stripJpeg(byte[] data) {
        if (data.length < 4 || u8(data, 0) != 0xFF || u8(data, 1) != SOI) {
            throw new IllegalArgumentException("JPEG 형식이 아닙니다.");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, 2);
        int pos = 2;
        while (pos < data.length) {
            if (u8(data, pos) != 0xFF) {
                throw new IllegalArgumentException("JPEG 구간 표시가 없습니다.");
            }
            // 채움 바이트(0xFF 여러 개) 건너뛰기
            int markerPos = pos;
            while (pos < data.length && u8(data, pos) == 0xFF) {
                pos++;
            }
            if (pos >= data.length) {
                throw new IllegalArgumentException("JPEG가 중간에 끝났습니다.");
            }
            int marker = u8(data, pos);
            pos++;
            if (marker == EOI) {
                out.write(0xFF);
                out.write(EOI);
                return out.toByteArray();
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                // 길이가 없는 표시
                out.write(0xFF);
                out.write(marker);
                continue;
            }
            if (pos + 2 > data.length) {
                throw new IllegalArgumentException("JPEG 구간 길이가 없습니다.");
            }
            int length = u16be(data, pos);
            if (length < 2 || pos + length > data.length) {
                throw new IllegalArgumentException("JPEG 구간 길이가 잘못됐습니다.");
            }
            int segmentStart = pos + 2;
            int segmentEnd = pos + length;
            if (marker == SOS) {
                // 압축된 그림 데이터부터 끝까지는 그대로 복사한다
                out.write(0xFF);
                out.write(SOS);
                out.write(data, pos, data.length - pos);
                return out.toByteArray();
            }
            if (marker == APP13) {
                pos = segmentEnd;
                continue;
            }
            if (marker == APP1) {
                byte[] payload = Arrays.copyOfRange(data, segmentStart, segmentEnd);
                if (startsWith(payload, EXIF_HEADER)) {
                    if (removeExifGps(payload, EXIF_HEADER.length)) {
                        out.write(0xFF);
                        out.write(APP1);
                        out.write(data, pos, 2);
                        out.write(payload, 0, payload.length);
                    }
                    // 구조가 이상하면 Exif 구간 전체를 뺀다
                }
                // XMP 등 다른 APP1은 뺀다
                pos = segmentEnd;
                continue;
            }
            out.write(data, markerPos, segmentEnd - markerPos);
            pos = segmentEnd;
        }
        throw new IllegalArgumentException("JPEG에 그림 데이터가 없습니다.");
    }

    /**
     * Exif(TIFF) 안의 GPS IFD를 비운다: GPS 항목들과 그 값이 가리키는 바이트를 0으로, 항목 수를 0으로.
     * 성공하면 true, 구조를 읽을 수 없으면 false.
     */
    static boolean removeExifGps(byte[] payload, int tiffStart) {
        try {
            int len = payload.length - tiffStart;
            if (len < 8) {
                return false;
            }
            boolean little;
            if (payload[tiffStart] == 'I' && payload[tiffStart + 1] == 'I') {
                little = true;
            } else if (payload[tiffStart] == 'M' && payload[tiffStart + 1] == 'M') {
                little = false;
            } else {
                return false;
            }
            Tiff tiff = new Tiff(payload, tiffStart, little);
            if (tiff.u16(2) != 42) {
                return false;
            }
            long ifd0 = tiff.u32(4);
            if (!tiff.inRange(ifd0, 2)) {
                return false;
            }
            int count = tiff.u16((int) ifd0);
            if (!tiff.inRange(ifd0 + 2, count * 12L)) {
                return false;
            }
            for (int i = 0; i < count; i++) {
                int entry = (int) ifd0 + 2 + i * 12;
                if (tiff.u16(entry) == 0x8825) {
                    long gpsIfd = tiff.u32(entry + 8);
                    return clearIfd(tiff, gpsIfd);
                }
            }
            return true; // GPS 없음
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    private static boolean clearIfd(Tiff tiff, long ifd) {
        if (!tiff.inRange(ifd, 2)) {
            return false;
        }
        int count = tiff.u16((int) ifd);
        if (!tiff.inRange(ifd + 2, count * 12L)) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            int entry = (int) ifd + 2 + i * 12;
            int type = tiff.u16(entry + 2);
            long n = tiff.u32(entry + 4);
            long size = typeSize(type) * n;
            if (size > 4) {
                long valueOffset = tiff.u32(entry + 8);
                if (tiff.inRange(valueOffset, size)) {
                    tiff.zero((int) valueOffset, (int) size);
                }
            }
            tiff.zero(entry, 12);
        }
        tiff.zero((int) ifd, 2); // 항목 수 0
        return true;
    }

    private static long typeSize(int type) {
        return switch (type) {
            case 1, 2, 6, 7 -> 1;
            case 3, 8 -> 2;
            case 4, 9, 11 -> 4;
            case 5, 10, 12 -> 8;
            default -> 0;
        };
    }

    /** TIFF 구조를 읽고 지우는 도우미. 위치는 TIFF 시작 기준. */
    private record Tiff(byte[] buf, int base, boolean little) {

        boolean inRange(long offset, long size) {
            return offset >= 0 && size >= 0 && base + offset + size <= buf.length;
        }

        int u16(int offset) {
            int a = buf[base + offset] & 0xFF;
            int b = buf[base + offset + 1] & 0xFF;
            return little ? (b << 8) | a : (a << 8) | b;
        }

        long u32(int offset) {
            long a = buf[base + offset] & 0xFF;
            long b = buf[base + offset + 1] & 0xFF;
            long c = buf[base + offset + 2] & 0xFF;
            long d = buf[base + offset + 3] & 0xFF;
            return little ? (d << 24) | (c << 16) | (b << 8) | a : (a << 24) | (b << 16) | (c << 8) | d;
        }

        void zero(int offset, int size) {
            Arrays.fill(buf, base + offset, base + offset + size, (byte) 0);
        }
    }

    // ── PNG ──────────────────────────────────────────────

    static byte[] stripPng(byte[] data) {
        if (data.length < 8) {
            throw new IllegalArgumentException("PNG 형식이 아닙니다.");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, 8);
        int pos = 8;
        boolean ended = false;
        while (pos < data.length) {
            if (pos + 12 > data.length) {
                throw new IllegalArgumentException("PNG 덩어리가 잘렸습니다.");
            }
            long length = u32be(data, pos);
            if (length > Integer.MAX_VALUE || pos + 12 + length > data.length) {
                throw new IllegalArgumentException("PNG 덩어리 길이가 잘못됐습니다.");
            }
            String type = new String(data, pos + 4, 4, StandardCharsets.ISO_8859_1);
            int chunkEnd = pos + 12 + (int) length;
            if (!type.equals("eXIf") && !type.equals("tEXt") && !type.equals("iTXt") && !type.equals("zTXt")) {
                out.write(data, pos, chunkEnd - pos);
            }
            pos = chunkEnd;
            if (type.equals("IEND")) {
                ended = true;
                break;
            }
        }
        if (!ended) {
            throw new IllegalArgumentException("PNG 끝 표시가 없습니다.");
        }
        return out.toByteArray();
    }

    // ── WebP ─────────────────────────────────────────────

    static byte[] stripWebp(byte[] data) {
        if (data.length < 12) {
            throw new IllegalArgumentException("WebP 형식이 아닙니다.");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, 12); // RIFF, 크기(나중에 고침), WEBP
        long riffEnd = Math.min(data.length, 8 + u32le(data, 4));
        int pos = 12;
        int vp8xFlagIndex = -1;
        while (pos + 8 <= riffEnd) {
            String fourcc = new String(data, pos, 4, StandardCharsets.ISO_8859_1);
            long size = u32le(data, pos + 4);
            long padded = size + (size & 1);
            if (pos + 8 + padded > data.length) {
                if (pos + 8 + size == data.length) {
                    padded = size; // 마지막 덩어리의 채움 바이트가 빠진 파일도 받는다
                } else {
                    throw new IllegalArgumentException("WebP 덩어리 길이가 잘못됐습니다.");
                }
            }
            int chunkEnd = (int) (pos + 8 + padded);
            if (!fourcc.equals("EXIF") && !fourcc.equals("XMP ")) {
                if (fourcc.equals("VP8X") && size >= 1) {
                    vp8xFlagIndex = out.size() + 8;
                }
                out.write(data, pos, chunkEnd - pos);
            }
            pos = chunkEnd;
        }
        byte[] result = out.toByteArray();
        if (vp8xFlagIndex >= 0) {
            result[vp8xFlagIndex] = (byte) (result[vp8xFlagIndex] & ~0x0C); // EXIF(0x08)·XMP(0x04) 표시 끄기
        }
        long riffSize = result.length - 8L;
        result[4] = (byte) riffSize;
        result[5] = (byte) (riffSize >> 8);
        result[6] = (byte) (riffSize >> 16);
        result[7] = (byte) (riffSize >> 24);
        return result;
    }

    // ── 공통 ─────────────────────────────────────────────

    private static int u8(byte[] data, int pos) {
        return data[pos] & 0xFF;
    }

    private static int u16be(byte[] data, int pos) {
        return (u8(data, pos) << 8) | u8(data, pos + 1);
    }

    private static long u32be(byte[] data, int pos) {
        return ((long) u8(data, pos) << 24) | ((long) u8(data, pos + 1) << 16) | ((long) u8(data, pos + 2) << 8)
                | u8(data, pos + 3);
    }

    private static long u32le(byte[] data, int pos) {
        return ((long) u8(data, pos + 3) << 24) | ((long) u8(data, pos + 2) << 16) | ((long) u8(data, pos + 1) << 8)
                | u8(data, pos);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
