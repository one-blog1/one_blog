package com.oneblog.file;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByStoredName(String storedName);
}
