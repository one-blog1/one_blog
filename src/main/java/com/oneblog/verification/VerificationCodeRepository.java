package com.oneblog.verification;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    Optional<VerificationCode> findTopByEmailAndPurposeOrderByCreatedAtDescIdDesc(String email,
            VerificationPurpose purpose);

    @Modifying
    @Query("delete from VerificationCode v where v.email = :email and v.purpose = :purpose")
    void deleteAllByEmailAndPurpose(@Param("email") String email, @Param("purpose") VerificationPurpose purpose);
}
