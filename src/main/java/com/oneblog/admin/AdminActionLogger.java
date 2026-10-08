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

    /** 관리자 열람 기록의 조치 종류 (D-106). */
    public static final String CONTENT_VIEW = "CONTENT_VIEW";
    private static final java.time.Duration VIEW_LOG_WINDOW = java.time.Duration.ofMinutes(10);

    /**
     * 관리자가 숨김·비공개·일부 공개 블로그나 숨긴 글을 열어 본 기록 (ADM-06, D-106).
     * 한 화면에서 여러 API가 같은 블로그를 확인하므로 같은 대상은 10분에 한 번만 남긴다.
     * 읽기 전용 조회 중에도 남도록 새 트랜잭션으로 쓴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logView(Long adminId, String targetType, Long targetId, String detail) {
        java.time.LocalDateTime since = java.time.LocalDateTime.now().minus(VIEW_LOG_WINDOW);
        if (repository.existsByAdminIdAndActionTypeAndTargetTypeAndTargetIdAndCreatedAtAfter(adminId, CONTENT_VIEW,
                targetType, targetId, since)) {
            return;
        }
        String ip = null;
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes attrs) {
            ip = attrs.getRequest().getRemoteAddr();
        }
        repository.save(AdminAction.of(adminId, CONTENT_VIEW, targetType, targetId, detail, ip));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void log(AuthenticatedUser admin, String actionType, String targetType, Long targetId, String detail,
            HttpServletRequest request) {
        repository.save(AdminAction.of(admin.id(), actionType, targetType, targetId, detail,
                request == null ? null : request.getRemoteAddr()));
    }
}
