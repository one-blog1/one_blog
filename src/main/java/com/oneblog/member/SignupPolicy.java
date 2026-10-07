package com.oneblog.member;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 가입 입력의 정리(정규화)와 규칙 (USR-01, SEC-02, 6.5, 6.7).
 * 화면도 같은 규칙을 검사하지만 서버가 최종 판단한다.
 */
@Component
public class SignupPolicy {

    /** 8~15자, 영문·숫자·특수문자 각각 1자 이상, 대소문자 구분 (SEC-02). */
    private static final Pattern PASSWORD = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S{8,15}$");
    /** 2~12자 한글·영문·숫자 (6.5). */
    private static final Pattern NICKNAME = Pattern.compile("^[가-힣A-Za-z0-9]{2,12}$");
    /** 휴대전화 번호, 숫자만 10~11자리. */
    private static final Pattern PHONE = Pattern.compile("^01[0-9]{8,9}$");
    /** 닉네임 예약어 (6.5). 비교는 소문자·공백 제거 후. */
    private static final Set<String> RESERVED_NICKNAMES = Set.of("관리자", "admin", "운영자", "탈퇴한회원");

    public String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizeName(String name) {
        return name == null ? null : name.strip();
    }

    public String normalizeNickname(String nickname) {
        return nickname == null ? null : nickname.strip();
    }

    /** 하이픈·공백 등 숫자가 아닌 문자를 뺀다. */
    public String normalizePhone(String phone) {
        return phone == null ? null : phone.replaceAll("[^0-9]", "");
    }

    public boolean isValidPassword(String password) {
        return password != null && PASSWORD.matcher(password).matches();
    }

    public boolean isValidName(String name) {
        return name != null && !name.isEmpty() && name.length() <= 50;
    }

    public boolean isValidNicknameFormat(String nickname) {
        return nickname != null && NICKNAME.matcher(nickname).matches();
    }

    public boolean isReservedNickname(String nickname) {
        if (nickname == null) {
            return false;
        }
        String key = nickname.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
        return RESERVED_NICKNAMES.contains(key);
    }

    public boolean isValidPhone(String phone) {
        return phone != null && PHONE.matcher(phone).matches();
    }
}
