package com.oneblog.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionRepository extends JpaRepository<AdminAction, Long> {

    Page<AdminAction> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
