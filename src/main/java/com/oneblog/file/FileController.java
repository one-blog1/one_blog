package com.oneblog.file;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.oneblog.common.security.AuthenticatedUser;

/** 이미지 올리기·받기 (contracts/blog-api.md, 6.3). */
@RestController
public class FileController {

    private final FileUploadService uploadService;
    private final StoredFileRepository repository;
    private final FileStorage storage;

    public FileController(FileUploadService uploadService, StoredFileRepository repository, FileStorage storage) {
        this.uploadService = uploadService;
        this.repository = repository;
        this.storage = storage;
    }

    @PostMapping("/api/files/blog-cover")
    public ResponseEntity<Map<String, Object>> uploadBlogCover(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file) {
        StoredFile stored = uploadService.uploadBlogCover(principal.id(), principal.role(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("fileId", stored.getId(), "url", stored.url()));
    }

    /** 이름 형식이 틀리거나 없거나 삭제된 파일은 404. 디스크 경로는 저장 이름 형식이 맞을 때만 만든다. */
    @GetMapping("/files/{storedName}")
    public ResponseEntity<InputStreamResource> download(@PathVariable("storedName") String storedName)
            throws IOException {
        if (!LocalFileStorage.STORED_NAME.matcher(storedName).matches()) {
            return ResponseEntity.notFound().build();
        }
        StoredFile file = repository.findByStoredName(storedName).orElse(null);
        if (file == null || file.isDeleted()) {
            return ResponseEntity.notFound().build();
        }
        InputStream in = storage.open(storedName);
        if (in == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(in));
    }
}
