package com.oneblog.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** 서버 디스크 저장 (D-75). 저장 폴더는 app.file.storage-dir. */
@Component
public class LocalFileStorage implements FileStorage {

    /** UUID + 확장자만 허용 (경로 조작 방지, 6.3). */
    static final Pattern STORED_NAME = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png|gif|webp)$");

    private final Path root;

    public LocalFileStorage(FileProperties properties) {
        this.root = Path.of(properties.storageDir()).toAbsolutePath().normalize();
    }

    @Override
    public void store(String storedName, byte[] content) throws IOException {
        Path target = resolve(storedName);
        Files.createDirectories(root);
        // 같은 이름이 있으면 덮어쓰지 않고 실패한다 (UUID라 겹칠 일은 없음)
        Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Override
    public InputStream open(String storedName) throws IOException {
        Path target = resolve(storedName);
        if (!Files.isRegularFile(target)) {
            return null;
        }
        return Files.newInputStream(target);
    }

    @Override
    public void delete(String storedName) throws IOException {
        Files.deleteIfExists(resolve(storedName));
    }

    private Path resolve(String storedName) {
        if (storedName == null || !STORED_NAME.matcher(storedName).matches()) {
            throw new IllegalArgumentException("저장 이름 형식이 아닙니다.");
        }
        Path target = root.resolve(storedName).normalize();
        if (!target.getParent().equals(root)) {
            throw new IllegalArgumentException("저장 폴더 밖의 경로입니다.");
        }
        return target;
    }
}
