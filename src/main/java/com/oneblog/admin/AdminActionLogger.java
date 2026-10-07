package com.oneblog.admin;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.common.security.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 관리자 조치를 기록한다 (ADM-06). 조치와 같은 트랜잭션에서 남겨, 조치가 취소되면 기록도 남지 않게 한다.
 * 접속 IP는 1차에 사용자에게서 직접 받은 주소(getRemoteAddr). 로드밸런서를 붙이면 프록시 헤더를 읽도록 바꾼다 (4.3).
 */
@Component
public class AdminActionLogger {

    private final AdminActionRepository repository;

    public AdminActionLogger(AdminActionRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void log(AuthenticatedUser admin, String actionType, String targetType, Long targetId, String detail,
            HttpServletRequest request) {
        repository.save(AdminAction.of(admin.id(), actionType, targetType, targetId, detail,
                request == null ? null : request.getRemoteAddr()));
    }
}
