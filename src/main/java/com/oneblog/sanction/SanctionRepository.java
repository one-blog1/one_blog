package com.oneblog.sanction;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 제재 기록(sanctions) 저장소. 경고·정지·강제 퇴장·블로그장 경고 횟수를 센다 (BLG-13, ADM-07). */
public interface SanctionRepository extends JpaRepository<Sanction, Long> {

    /** 이 블로그에서 이 사람이 받은 제재 수 (1년 안, 해제 안 된 것). 정지 3번(BLG-13), 블로그장 경고 3번(ADM-07). */
    @Query("""
            select count(s) from Sanction s
            where s.blogId = :blogId and s.userId = :userId and s.type = :type and s.createdAt >= :since
            """)
    long countSince(@Param("blogId") Long blogId, @Param("userId") Long userId, @Param("type") SanctionType type,
            @Param("since") LocalDateTime since);

    @Query("""
            select s from Sanction s
            where s.blogId = :blogId and s.userId = :userId and s.type = :type and s.releasedAt is null
            order by s.id desc
            """)
    List<Sanction> findOpen(@Param("blogId") Long blogId, @Param("userId") Long userId,
            @Param("type") SanctionType type);
}
