package com.oneblog.member;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글·댓글 작성자 이름 표시. 탈퇴한 회원은 "탈퇴한 회원"(D-05), 블로그를 떠난 작성자는 "탈퇴한 계정"(D-33).
 * 여러 작성자를 한 번에 읽는다 (목록에서 작성자마다 따로 조회하지 않게).
 */
@Service
public class UserDisplayService {

    public static final String WITHDRAWN_MEMBER = "탈퇴한 회원";
    public static final String DETACHED_ACCOUNT = "탈퇴한 계정";

    private final UserRepository userRepository;

    public UserDisplayService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 회원 ID → 화면에 보일 이름. 탈퇴했거나 없는 회원은 "탈퇴한 회원". */
    @Transactional(readOnly = true)
    public Map<Long, String> names(Collection<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        for (Object[] row : userRepository.findNicknameRows(userIds)) {
            Long id = (Long) row[0];
            String nickname = (String) row[1];
            UserStatus status = (UserStatus) row[2];
            result.put(id, status == UserStatus.ACTIVE && nickname != null ? nickname : WITHDRAWN_MEMBER);
        }
        for (Long id : userIds) {
            result.putIfAbsent(id, WITHDRAWN_MEMBER);
        }
        return result;
    }

    /** 작성자 표시 이름: 블로그와 연결이 끊긴 작성자는 "탈퇴한 계정". */
    public static String authorName(Map<Long, String> names, Long userId, boolean detached) {
        if (detached) {
            return DETACHED_ACCOUNT;
        }
        return names.getOrDefault(userId, WITHDRAWN_MEMBER);
    }
}
