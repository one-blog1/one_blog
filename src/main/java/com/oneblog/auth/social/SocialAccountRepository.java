package com.oneblog.auth.social;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 소셜 계정 연결(social_accounts) 저장소 (D-126). */
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    List<SocialAccount> findByUserIdOrderByIdAsc(Long userId);

    boolean existsByUserIdAndProvider(Long userId, SocialProvider provider);

    @Modifying
    @Query("delete from SocialAccount s where s.userId = :userId and s.provider = :provider")
    int deleteLink(@Param("userId") Long userId, @Param("provider") SocialProvider provider);

    @Modifying
    @Query("delete from SocialAccount s where s.userId = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);
}
