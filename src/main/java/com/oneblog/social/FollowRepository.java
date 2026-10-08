package com.oneblog.social;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.oneblog.member.UserStatus;

/** 팔로우(follows) 저장소. 팔로워·팔로잉 수와 팔로우 여부에 쓴다 (SOC-01). */
public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    @Query("""
            select count(f) from Follow f, User u
            where u.id = f.followerId and f.followeeId = :userId and u.status = :active
            """)
    long countFollowerRows(@Param("userId") Long userId, @Param("active") UserStatus active);

    @Query("""
            select count(f) from Follow f, User u
            where u.id = f.followeeId and f.followerId = :userId and u.status = :active
            """)
    long countFollowingRows(@Param("userId") Long userId, @Param("active") UserStatus active);

    /** 활성 회원만 센다 (탈퇴한 회원은 목록·수에서 빠진다). */
    default long countFollowers(Long userId) {
        return countFollowerRows(userId, UserStatus.ACTIVE);
    }

    default long countFollowing(Long userId) {
        return countFollowingRows(userId, UserStatus.ACTIVE);
    }

    /** 서로의 팔로우를 끊는다 (차단 SOC-05, 013). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from Follow f
            where (f.followerId = :a and f.followeeId = :b) or (f.followerId = :b and f.followeeId = :a)
            """)
    int deleteBetween(@Param("a") Long a, @Param("b") Long b);
}
