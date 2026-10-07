package com.oneblog.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.member.UserRole;
import com.oneblog.member.ValidationFailedException;

/**
 * 이미지 올리기 (6.3, SEC-08, research R4).
 * 확장자·MIME 타입·파일 앞부분이 모두 같은 허용 형식이어야 받고, 위치 정보를 지운 뒤 UUID 이름으로 저장한다.
 */
@Service
public class FileUploadService {

    /** 프로필·블로그 대표 이미지 한 장의 상한 (6.3). */
    public static final long COVER_MAX_BYTES = 3L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    private final FileStorage storage;
    private final StoredFileRepository repository;

    public FileUploadService(FileStorage storage, StoredFileRepository repository) {
        this.storage = storage;
        this.repository = repository;
    }

    @Transactional
    public StoredFile uploadBlogCover(Long userId, UserRole role, MultipartFile file) {
        if (role == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_ALLOWED", "관리자 계정은 블로그 활동을 할 수 없습니다.");
        }
        return upload(userId, FilePurpose.BLOG_COVER, file, COVER_MAX_BYTES);
    }

    private StoredFile upload(Long userId, FilePurpose purpose, MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new ValidationFailedException(List.of(new ErrorResponse.FieldError("file", "파일을 골라 주세요.")));
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE",
                    "파일은 " + (maxBytes / 1024 / 1024) + "MB까지 올릴 수 있습니다.");
        }
        byte[] data = readBytes(file);
        ImageType byName = ImageType.fromFilename(file.getOriginalFilename());
        ImageType byMime = ImageType.fromMimeType(file.getContentType());
        ImageType byContent = ImageTypeDetector.detect(data);
        if (byName == null || byName != byMime || byName != byContent) {
            throw unsupported();
        }

        byte[] cleaned;
        try {
            cleaned = ImageMetadataStripper.strip(byContent, data);
        } catch (IllegalArgumentException e) {
            throw unsupported();
        }

        String storedName = UUID.randomUUID() + "." + byContent.extension();
        try {
            storage.store(storedName, cleaned);
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 저장하지 못했습니다.", e);
        }
        try {
            return repository.saveAndFlush(StoredFile.create(userId, purpose, storedName,
                    originalName(file.getOriginalFilename()), byContent.mimeType(), cleaned.length));
        } catch (RuntimeException e) {
            deleteQuietly(storedName);
            throw e;
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 읽지 못했습니다.", e);
        }
    }

    /** 경로 부분을 떼고 255자로 자른다. DB 기록용이고 저장 경로·화면에는 쓰지 않는다. */
    static String originalName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "image";
        }
        String name = raw.substring(Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\')) + 1).strip();
        if (name.isEmpty()) {
            return "image";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private void deleteQuietly(String storedName) {
        try {
            storage.delete(storedName);
        } catch (IOException | RuntimeException e) {
            log.warn("저장하지 못한 업로드 파일을 지우지 못했습니다: {}", storedName, e);
        }
    }

    private static ApiException unsupported() {
        return new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE",
                "jpg, jpeg, png, gif, webp 이미지만 올릴 수 있습니다.");
    }
}
