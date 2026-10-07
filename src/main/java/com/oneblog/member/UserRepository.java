package com.oneblog.member;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<User> findByNickname(String nickname);

    /** 회원 행을 잠그고 읽는다 (SELECT ... FOR UPDATE). 블로그 개수 제한을 동시 요청에도 지키기 위해 (D-68). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdateById(@Param("id") Long id);

    /** 결과 행은 [id, nickname, status]. 작성자 이름 표시용 (UserDisplayService). */
    @Query("select u.id, u.nickname, u.status from User u where u.id in :ids")
    List<Object[]> findNicknameRows(@Param("ids") Collection<Long> ids);
}
