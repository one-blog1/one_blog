package com.oneblog.admin;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 관리자 활동 기록(admin_actions) 저장소. 활동 기록 목록과 열람 기록 중복 확인(D-106)에 쓴다. */
public interface AdminActionRepository extends JpaRepository<AdminAction, Long> {

    Page<AdminAction> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    boolean existsByAdminIdAndActionTypeAndTargetTypeAndTargetIdAndCreatedAtAfter(Long adminId, String actionType,
            String targetType, Long targetId, LocalDateTime after);
}
