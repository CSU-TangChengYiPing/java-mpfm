package com.mpfm.backend.application.user;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 验证码服务，负责验证码签发、存储与答案校验。
 */
@Service
public class CaptchaService {

    private static final long EXPIRE_SECONDS = 300;
    private static final int MAX_VERIFY_FAIL = 5;
    private static final int DEFAULT_LENGTH = 5;
    private static final int CAPTCHA_WIDTH = 168;
    private static final int CAPTCHA_HEIGHT = 52;

    private final Map<String, CaptchaValue> store = new ConcurrentHashMap<>();
    private final String fixedAnswer;

    public CaptchaService(@Value("${mpfm.captcha.fixed-answer:}") String fixedAnswer) {
        this.fixedAnswer = fixedAnswer;
    }

    public CaptchaIssue issue(String scene) {
        String captchaId = UUID.randomUUID().toString();
        CaptchaImage captchaImage = fixedAnswer == null || fixedAnswer.isBlank()
                ? generateComplexCaptcha()
                : generateFixedCaptcha(fixedAnswer);
        String answer = captchaImage.answer();
        store.put(captchaId, new CaptchaValue(answer, Instant.now().plusSeconds(EXPIRE_SECONDS), 0, false));
        return new CaptchaIssue(
                captchaId,
                scene == null ? "login" : scene,
                "验证码已发放",
                EXPIRE_SECONDS,
                captchaImage.imageDataUrl());
    }

    public void verify(String captchaId, String answer) {
        CaptchaValue value = store.get(captchaId);
        if (value == null || value.used() || Instant.now().isAfter(value.expireAt())) {
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID, "captcha invalid");
        }
        if (!value.answer().equals(answer == null ? "" : answer.toLowerCase(Locale.ROOT))) {
            int fail = value.verifyFailCount() + 1;
            store.put(captchaId, new CaptchaValue(value.answer(), value.expireAt(), fail, value.used()));
            if (fail >= MAX_VERIFY_FAIL) {
                store.remove(captchaId);
            }
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID, "captcha invalid");
        }
        store.put(captchaId, new CaptchaValue(value.answer(), value.expireAt(), value.verifyFailCount(), true));
    }

    /** 验证码签发结果模型，返回验证码标识、展示内容与过期时间。 */
    public record CaptchaIssue(String captchaId, String scene, String message, long expiresInSeconds, String imageDataUrl) {
    }

    private record CaptchaValue(String answer, Instant expireAt, int verifyFailCount, boolean used) {
    }

    private CaptchaImage generateComplexCaptcha() {
        SpecCaptcha captcha = new SpecCaptcha(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, DEFAULT_LENGTH);
        captcha.setCharType(Captcha.TYPE_DEFAULT);
        String base64 = captcha.toBase64();
        return new CaptchaImage(normalizeBase64(base64), captcha.text().toLowerCase(Locale.ROOT));
    }

    private CaptchaImage generateFixedCaptcha(String fixed) {
        String answer = fixed.toLowerCase(Locale.ROOT);
        SpecCaptcha captcha = new SpecCaptcha(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, Math.max(answer.length(), DEFAULT_LENGTH));
        captcha.setCharType(Captcha.TYPE_ONLY_NUMBER);
        String base64 = captcha.toBase64();
        return new CaptchaImage(normalizeBase64(base64), answer);
    }

    private String normalizeBase64(String base64) {
        if (base64.startsWith("data:image")) {
            return base64;
        }
        return "data:image/png;base64," + base64;
    }

    private record CaptchaImage(String imageDataUrl, String answer) {
    }
}




