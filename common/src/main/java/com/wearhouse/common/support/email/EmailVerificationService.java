package com.wearhouse.common.support.email;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    @Value("${wearhouse.email-auth.code-ttl-seconds:180}")
    private long codeTtlSeconds;
    @Value("${wearhouse.email-auth.verified-ttl-seconds:900}")
    private long verifiedTtlSeconds;
    @Value("${wearhouse.email-auth.code-key-prefix:wearhouse:email:code:}")
    private String codeKeyPrefix;
    @Value("${wearhouse.email-auth.verified-key-prefix:wearhouse:email:verified:}")
    private String verifiedKeyPrefix;
    @Value("${spring.mail.username:}")
    private String fromAddress;

    public void sendCode(String email) {
        String authCode = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        redisTemplate.opsForValue().set(codeKey(email), authCode, Duration.ofSeconds(codeTtlSeconds));

        SimpleMailMessage message = new SimpleMailMessage();
        if (!fromAddress.isBlank()) {
            message.setFrom(fromAddress);
        }
        message.setTo(email);
        message.setSubject("[Wearhouse] 이메일 인증 코드");
        message.setText("인증 코드: " + authCode + "\n유효 시간: 3분");

        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender == null) {
            throw new IllegalStateException("JavaMailSender bean is not configured.");
        }

        try {
            javaMailSender.send(message);
        } catch (MailException exception) {
            log.error("Failed to send verification email to {}", email, exception);
            throw exception;
        }
    }

    public boolean verifyCodeAndMarkVerified(String email, String inputCode) {
        String stored = redisTemplate.opsForValue().get(codeKey(email));
        if (stored == null || !stored.equals(inputCode)) {
            return false;
        }
        redisTemplate.delete(codeKey(email));
        redisTemplate.opsForValue().set(verifiedKey(email), "true", Duration.ofSeconds(verifiedTtlSeconds));
        return true;
    }

    public boolean isVerified(String email) {
        return "true".equals(redisTemplate.opsForValue().get(verifiedKey(email)));
    }

    public void clearVerified(String email) {
        redisTemplate.delete(verifiedKey(email));
    }

    private String codeKey(String email) {
        return codeKeyPrefix + email;
    }

    private String verifiedKey(String email) {
        return verifiedKeyPrefix + email;
    }
}
