package com.oneblog.admin;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** 관리자 활동 기록 (Crowfoot ERD admin_actions, ADM-06, 4.4 개인정보 전체 보기 기록). 1년 보관 (4.5). */
@Entity
@Table(name = "admin_actions")
public class AdminAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "action_type", length = 40, nullable = false)
    private String actionType;

    @Column(name = "target_type", length = 20, nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AdminAction() {
    }

    public static AdminAction of(Long adminId, String actionType, String targetType, Long targetId, String detail,
            String ipAddress) {
        AdminAction action = new AdminAction();
        action.adminId = adminId;
        action.actionType = actionType;
        action.targetType = targetType;
        action.targetId = targetId;
        action.detail = detail == null ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail);
        action.ipAddress = ipAddress == null ? null : (ipAddress.length() > 45 ? ipAddress.substring(0, 45) : ipAddress);
        return action;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getAdminId() {
        return adminId;
    }

    public String getActionType() {
        return actionType;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
