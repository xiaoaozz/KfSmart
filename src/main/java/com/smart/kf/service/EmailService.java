package com.smart.kf.service;

import com.smart.kf.exception.CustomException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private static final String OTP_KEY_PREFIX   = "email:code:";
    private static final String LIMIT_KEY_PREFIX = "email:code:limit:";
    private static final long   OTP_TTL_SECONDS   = 300L;
    private static final long   RATE_LIMIT_SECONDS = 60L;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${spring.mail.username}")
    private String fromAddress;

    /**
     * Send a 6-digit OTP to the given email address.
     * Rate-limited to once per 60 seconds per email.
     */
    public void sendRegistrationCode(String email) {
        validateEmailFormat(email);

        String limitKey = LIMIT_KEY_PREFIX + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            throw new CustomException("验证码已发送，请60秒后再试", HttpStatus.TOO_MANY_REQUESTS);
        }

        String code = generateOtp();
        redisTemplate.opsForValue().set(OTP_KEY_PREFIX + email, code, OTP_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(limitKey, "1", RATE_LIMIT_SECONDS, TimeUnit.SECONDS);

        sendHtmlMail(email, code);
        logger.info("Registration OTP sent to: {}", maskEmail(email));
    }

    /**
     * Verify the OTP. Deletes the stored code on success (one-time use).
     */
    public void verifyRegistrationCode(String email, String code) {
        String otpKey = OTP_KEY_PREFIX + email;
        Object stored = redisTemplate.opsForValue().get(otpKey);
        if (stored == null) {
            throw new CustomException("验证码已过期，请重新获取", HttpStatus.BAD_REQUEST);
        }
        if (!code.equals(stored.toString())) {
            throw new CustomException("验证码不正确", HttpStatus.BAD_REQUEST);
        }
        redisTemplate.delete(otpKey);
        redisTemplate.delete(LIMIT_KEY_PREFIX + email);
    }

    // ------------------------------------------------------------------ private

    private String generateOtp() {
        SecureRandom rng = new SecureRandom();
        return String.format("%06d", rng.nextInt(900_000) + 100_000);
    }

    private void sendHtmlMail(String to, String code) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("KfSmart 注册验证码");
            helper.setText(
                "<div style='font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:32px;border:1px solid #e5e7eb;border-radius:8px'>" +
                "<h2 style='color:#1d4ed8;margin-bottom:8px'>KfSmart</h2>" +
                "<p style='color:#374151'>您好，感谢注册 KfSmart AI Platform。</p>" +
                "<p style='color:#374151'>您的邮箱验证码为：</p>" +
                "<div style='font-size:32px;font-weight:bold;letter-spacing:8px;color:#1d4ed8;padding:16px 0'>" + code + "</div>" +
                "<p style='color:#6b7280;font-size:13px'>验证码 <strong>5 分钟</strong>内有效，请勿转发给他人。</p>" +
                "</div>",
                true
            );
            mailSender.send(msg);
        } catch (Exception e) {
            logger.error("Failed to send OTP email to {}: {}", maskEmail(to), e.getMessage());
            throw new CustomException("邮件发送失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateEmailFormat(String email) {
        if (email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new CustomException("邮箱格式不正确", HttpStatus.BAD_REQUEST);
        }
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }
}
