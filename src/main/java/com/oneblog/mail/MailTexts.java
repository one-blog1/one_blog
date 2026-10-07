package com.oneblog.mail;

/**
 * 메일 제목과 본문. HTML 본문과, HTML을 못 보여 주는 메일 앱을 위한 평문 본문을 함께 둔다.
 * 본문에는 사용자 입력을 넣지 않는다 (인증번호는 서버가 만든 숫자 6자리뿐).
 */
final class MailTexts {

    static final String SENDER_NAME = "One Blog";
    static final String SIGNUP_CODE_SUBJECT = "[One Blog] 회원가입 인증번호";
    static final String ALREADY_REGISTERED_SUBJECT = "[One Blog] 회원가입 안내";
    static final String PASSWORD_RESET_SUBJECT = "[One Blog] 비밀번호 재설정 인증번호";

    private static final String ACCENT = "#0F766E";

    private MailTexts() {
    }

    static String signupCode(String code) {
        return """
                One Blog 회원가입 인증번호입니다.

                인증번호: %s

                10분 안에 가입 화면에 입력해 주세요. 5번 틀리면 새 번호를 받아야 합니다.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(code);
    }

    static String signupCodeHtml(String code) {
        String body = """
                <h1 style="margin:0 0 12px;font-size:22px;line-height:1.4;color:#17191C;">회원가입 인증번호</h1>
                <p style="margin:0 0 24px;font-size:15px;line-height:1.7;color:#3F444B;">
                  One Blog 가입 화면에 아래 인증번호 6자리를 입력해 주세요.
                </p>
                <div style="margin:0 0 24px;padding:20px 0;border-radius:12px;background:#E3F2EF;text-align:center;">
                  <span style="font-size:34px;font-weight:700;letter-spacing:10px;color:%s;font-family:'SFMono-Regular',Menlo,Consolas,monospace;">%s</span>
                </div>
                <p style="margin:0;font-size:14px;line-height:1.7;color:#5B616B;">
                  인증번호는 <strong style="color:#17191C;">10분 동안</strong> 쓸 수 있고, 5번 틀리면 새 번호를 받아야 합니다.<br>
                  본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                </p>
                """.formatted(ACCENT, code);
        return layout("One Blog 회원가입 인증번호 " + code, body);
    }

    static String passwordResetCode(String code) {
        return """
                One Blog 비밀번호 재설정 인증번호입니다.

                인증번호: %s

                30분 안에 비밀번호 찾기 화면에 입력해 주세요. 한 번만 쓸 수 있고, 5번 틀리면 새 번호를 받아야 합니다.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않습니다.
                """.formatted(code);
    }

    static String passwordResetCodeHtml(String code) {
        String body = """
                <h1 style="margin:0 0 12px;font-size:22px;line-height:1.4;color:#17191C;">비밀번호 재설정 인증번호</h1>
                <p style="margin:0 0 24px;font-size:15px;line-height:1.7;color:#3F444B;">
                  One Blog 비밀번호 찾기 화면에 아래 인증번호 6자리를 입력해 주세요.
                </p>
                <div style="margin:0 0 24px;padding:20px 0;border-radius:12px;background:#E3F2EF;text-align:center;">
                  <span style="font-size:34px;font-weight:700;letter-spacing:10px;color:%s;font-family:'SFMono-Regular',Menlo,Consolas,monospace;">%s</span>
                </div>
                <p style="margin:0;font-size:14px;line-height:1.7;color:#5B616B;">
                  인증번호는 <strong style="color:#17191C;">30분 동안 한 번만</strong> 쓸 수 있고, 5번 틀리면 새 번호를 받아야 합니다.<br>
                  본인이 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않습니다.
                </p>
                """.formatted(ACCENT, code);
        return layout("One Blog 비밀번호 재설정 인증번호 " + code, body);
    }

    static String alreadyRegistered() {
        return """
                이 이메일로 One Blog 회원가입을 시도했지만, 이미 가입된 이메일입니다.

                로그인 화면에서 로그인해 주세요.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """;
    }

    static String alreadyRegisteredHtml() {
        String body = """
                <h1 style="margin:0 0 12px;font-size:22px;line-height:1.4;color:#17191C;">이미 가입된 이메일입니다</h1>
                <p style="margin:0 0 24px;font-size:15px;line-height:1.7;color:#3F444B;">
                  이 이메일로 One Blog 회원가입을 시도했지만, 이미 가입된 계정이 있습니다.<br>
                  로그인 화면에서 이 이메일로 로그인해 주세요.
                </p>
                <p style="margin:0;font-size:14px;line-height:1.7;color:#5B616B;">
                  본인이 요청하지 않았다면 이 메일을 무시해 주세요. 계정에는 아무 변화가 없습니다.
                </p>
                """;
        return layout("이미 가입된 이메일입니다", body);
    }

    /** 메일 앱 호환을 위해 표(table)와 인라인 스타일만 쓴다. */
    private static String layout(String preheader, String body) {
        return """
                <!doctype html>
                <html lang="ko">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#F6F7F9;">
                <span style="display:none;max-height:0;overflow:hidden;opacity:0;">%s</span>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#F6F7F9;">
                  <tr><td align="center" style="padding:32px 16px;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;font-family:'Apple SD Gothic Neo','Malgun Gothic','Noto Sans KR',sans-serif;">
                      <tr><td style="padding:0 4px 16px;">
                        <span style="display:inline-block;width:28px;height:28px;line-height:28px;border-radius:8px;background:%s;color:#FFFFFF;font-size:15px;font-weight:700;text-align:center;vertical-align:middle;">O</span>
                        <span style="font-size:18px;font-weight:700;color:#17191C;vertical-align:middle;margin-left:6px;">One Blog</span>
                      </td></tr>
                      <tr><td style="background:#FFFFFF;border:1px solid #E3E6EA;border-radius:16px;padding:36px 32px;">
                        %s
                      </td></tr>
                      <tr><td style="padding:16px 4px 0;font-size:12px;line-height:1.6;color:#868C95;">
                        이 메일은 발신 전용입니다. 답장을 보내도 확인할 수 없습니다.
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(preheader, ACCENT, body);
    }
}
