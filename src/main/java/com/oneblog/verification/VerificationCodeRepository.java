package com.oneblog.verification;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 이메일 인증번호(verification_codes) 저장소. 가입·비밀번호 재설정 인증에 쓴다 (USR-02, USR-06). */
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    Optional<VerificationCode> findTopByEmailAndPurposeOrderByCreatedAtDescIdDesc(String email,
            VerificationPurpose purpose);

    @Modifying
    @Query("delete from VerificationCode v where v.email = :email and v.purpose = :purpose")
    void deleteAllByEmailAndPurpose(@Param("email") String email, @Param("purpose") VerificationPurpose purpose);
}
