package com.oneblog.file;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 올린 파일(files) 저장소. 프로필 사진·블로그 표지·글 이미지 확인과 정리에 쓴다 (BRD-04). */
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByStoredName(String storedName);

    List<StoredFile> findByStoredNameIn(Collection<String> storedNames);

    /** 글에 연결된, 지워지지 않은 이미지. */
    @Query("select f from StoredFile f where f.postId = :postId and f.deletedAt is null")
    List<StoredFile> findAttached(@Param("postId") Long postId);

    /** 글별 이미지(목록 썸네일은 첫 장). 결과 행은 [postId, storedName], 글·순서대로. */
    @Query("""
            select f.postId, f.storedName from StoredFile f
            where f.postId in :postIds and f.deletedAt is null and f.purpose = :purpose
            order by f.postId asc, f.sortOrder asc, f.id asc
            """)
    List<Object[]> findImageRows(@Param("postIds") Collection<Long> postIds, @Param("purpose") FilePurpose purpose);
}
