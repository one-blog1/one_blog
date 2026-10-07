package com.oneblog.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/** 위치 정보 지우기 (6.3, research R4). */
class ImageMetadataStripperTest {

    /** 위도 값으로 넣을 알아보기 쉬운 바이트 */
    private static final byte[] GPS_MARK = "GPSGPSGPSGPSGPSGPSGPSGPS".getBytes(StandardCharsets.US_ASCII);

    @Test
    void JPEG의_GPS는_지우고_사진_방향은_남긴다() throws Exception {
        byte[] jpeg = jpegWithExifGpsAndXmp();
        byte[] cleaned = ImageMetadataStripper.strip(ImageType.JPEG, jpeg);

        assertThat(indexOf(cleaned, GPS_MARK)).isEqualTo(-1);
        assertThat(indexOf(cleaned, "http://ns.adobe.com/xap/1.0/".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(-1);
        assertThat(indexOf(cleaned, "Exif".getBytes(StandardCharsets.US_ASCII))).isPositive();
        // 방향(0x0112) 항목과 값 6이 그대로 있다 (little endian: 12 01 03 00 01 00 00 00 06 00)
        assertThat(indexOf(cleaned, new byte[] {0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x00}))
                .isPositive();
        assertThat(cleaned.length).isEqualTo(jpeg.length - xmpSegmentLength());
        // 그림은 그대로 읽힌다
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(cleaned))).isNotNull();
    }

    @Test
    void 메타데이터가_없는_JPEG는_그대로다() {
        byte[] jpeg = TestImages.jpeg();
        assertThat(ImageMetadataStripper.strip(ImageType.JPEG, jpeg)).isEqualTo(jpeg);
    }

    @Test
    void PNG의_텍스트와_eXIf_덩어리를_뺀다() throws Exception {
        byte[] png = TestImages.png();
        byte[] withText = insertPngChunk(png, "tEXt", "Location\0Seoul 37.5,127.0");
        byte[] withExif = insertPngChunk(withText, "eXIf", "MM\0*GPSGPS");

        byte[] cleaned = ImageMetadataStripper.strip(ImageType.PNG, withExif);
        assertThat(cleaned).isEqualTo(png);
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(cleaned))).isNotNull();
    }

    @Test
    void WebP의_EXIF_XMP_덩어리를_빼고_크기를_고친다() {
        byte[] base = TestImages.webp();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(java.util.Arrays.copyOfRange(base, 0, 12));
        // VP8X (EXIF·XMP 표시 켜짐)
        out.writeBytes("VP8X".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(TestImages.le32(10));
        out.writeBytes(new byte[] {0x0C, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        out.writeBytes(java.util.Arrays.copyOfRange(base, 12, base.length));
        out.writeBytes("EXIF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(TestImages.le32(GPS_MARK.length));
        out.writeBytes(GPS_MARK);
        out.writeBytes("XMP ".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(TestImages.le32(3));
        out.writeBytes(new byte[] {'x', 'm', 'p', 0});
        byte[] input = out.toByteArray();
        int riff = input.length - 8;
        System.arraycopy(TestImages.le32(riff), 0, input, 4, 4);

        byte[] cleaned = ImageMetadataStripper.strip(ImageType.WEBP, input);
        assertThat(indexOf(cleaned, GPS_MARK)).isEqualTo(-1);
        assertThat(indexOf(cleaned, "XMP ".getBytes(StandardCharsets.US_ASCII))).isEqualTo(-1);
        assertThat(cleaned[20] & 0x0C).isZero(); // VP8X 표시 꺼짐
        int size = (cleaned[4] & 0xFF) | (cleaned[5] & 0xFF) << 8 | (cleaned[6] & 0xFF) << 16 | (cleaned[7] & 0xFF) << 24;
        assertThat(size).isEqualTo(cleaned.length - 8);
        assertThat(ImageTypeDetector.detect(cleaned)).isEqualTo(ImageType.WEBP);
    }

    @Test
    void 구조가_깨진_파일은_거부한다() {
        byte[] jpeg = TestImages.jpeg();
        byte[] truncated = java.util.Arrays.copyOf(jpeg, 20);
        assertThatThrownBy(() -> ImageMetadataStripper.strip(ImageType.JPEG, truncated))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] png = TestImages.png();
        byte[] noEnd = java.util.Arrays.copyOf(png, png.length - 12);
        assertThatThrownBy(() -> ImageMetadataStripper.strip(ImageType.PNG, noEnd))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 테스트 데이터 만들기 ──────────────────────────────

    private static final byte[] XMP = ("http://ns.adobe.com/xap/1.0/\0<x:xmpmeta><exif:GPSLatitude>37</exif:GPSLatitude>"
            + "</x:xmpmeta>").getBytes(StandardCharsets.UTF_8);

    private static int xmpSegmentLength() {
        return 2 + 2 + XMP.length;
    }

    /** SOI 뒤에 Exif(방향 6 + GPS 위도) APP1과 XMP APP1을 넣은 JPEG. */
    private static byte[] jpegWithExifGpsAndXmp() {
        // TIFF (little endian). IFD0 at 8: 2 entries → 8 + 2 + 24 + 4 = 38 → GPS IFD at 38
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.writeBytes(new byte[] {'I', 'I', 42, 0});
        tiff.writeBytes(TestImages.le32(8));
        tiff.writeBytes(new byte[] {2, 0});
        tiff.writeBytes(new byte[] {0x12, 0x01, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0});       // Orientation = 6
        tiff.writeBytes(new byte[] {0x25, (byte) 0x88, 4, 0, 1, 0, 0, 0});           // GPSInfo, LONG, 1
        tiff.writeBytes(TestImages.le32(38));
        tiff.writeBytes(TestImages.le32(0));                                         // 다음 IFD 없음
        // GPS IFD at 38: 1 entry → 38 + 2 + 12 + 4 = 56 → 값 at 56
        tiff.writeBytes(new byte[] {1, 0});
        tiff.writeBytes(new byte[] {0x02, 0x00, 5, 0, 3, 0, 0, 0});                  // GPSLatitude, RATIONAL, 3
        tiff.writeBytes(TestImages.le32(56));
        tiff.writeBytes(TestImages.le32(0));
        tiff.writeBytes(GPS_MARK);                                                   // 24바이트 = RATIONAL 3개
        byte[] exifPayload = concat("Exif\0\0".getBytes(StandardCharsets.US_ASCII), tiff.toByteArray());

        byte[] base = TestImages.jpeg();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        writeSegment(out, 0xE1, exifPayload);
        writeSegment(out, 0xE1, XMP);
        out.write(base, 2, base.length - 2);
        return out.toByteArray();
    }

    private static void writeSegment(ByteArrayOutputStream out, int marker, byte[] payload) {
        int length = payload.length + 2;
        out.write(0xFF);
        out.write(marker);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(payload);
    }

    /** IHDR 바로 뒤에 덩어리 하나를 넣는다. */
    private static byte[] insertPngChunk(byte[] png, String type, String text) {
        int ihdrEnd = 8 + 12 + 13;
        byte[] data = text.getBytes(StandardCharsets.ISO_8859_1);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        long c = crc.getValue();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, ihdrEnd);
        out.writeBytes(new byte[] {(byte) (data.length >> 24), (byte) (data.length >> 16), (byte) (data.length >> 8),
                (byte) data.length});
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        out.writeBytes(new byte[] {(byte) (c >> 24), (byte) (c >> 16), (byte) (c >> 8), (byte) c});
        out.write(png, ihdrEnd, png.length - ihdrEnd);
        return out.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
