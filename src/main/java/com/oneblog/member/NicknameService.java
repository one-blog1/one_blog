package com.oneblog.member;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.member.dto.NicknameAvailabilityResponse;

/** 닉네임 사용 가능 여부 (6.5). 최종 판단은 가입 완료 요청에서 다시 한다. */
@Service
public class NicknameService {

    private final UserRepository userRepository;
    private final SignupPolicy signupPolicy;

    public NicknameService(UserRepository userRepository, SignupPolicy signupPolicy) {
        this.userRepository = userRepository;
        this.signupPolicy = signupPolicy;
    }

    @Transactional(readOnly = true)
    public NicknameAvailabilityResponse check(String rawNickname) {
        String nickname = signupPolicy.normalizeNickname(rawNickname);
        if (!signupPolicy.isValidNicknameFormat(nickname)) {
            return NicknameAvailabilityResponse.unavailable("INVALID_FORMAT");
        }
        if (signupPolicy.isReservedNickname(nickname)) {
            return NicknameAvailabilityResponse.unavailable("RESERVED");
        }
        if (userRepository.existsByNickname(nickname)) {
            return NicknameAvailabilityResponse.unavailable("TAKEN");
        }
        return NicknameAvailabilityResponse.ok();
    }
}
