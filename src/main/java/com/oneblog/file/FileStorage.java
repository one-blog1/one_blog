package com.oneblog.file;

import java.io.IOException;
import java.io.InputStream;

/**
 * 파일 바이트 저장소 (SCL-02). 1차는 서버 디스크(LocalFileStorage), 이중화 때 S3 구현으로 바꾼다 (D-75).
 * 이름은 호출하는 쪽이 만든 UUID 이름만 받는다. 경로 조작을 막기 위해 구현은 이름 형식을 다시 확인한다.
 */
public interface FileStorage {

    void store(String storedName, byte[] content) throws IOException;

    /** 없으면 null. */
    InputStream open(String storedName) throws IOException;

    void delete(String storedName) throws IOException;
}
