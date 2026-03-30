package com.wearhouse.payment.webhook.security;

import com.wearhouse.payment.support.config.PaymentWebhookProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PaymentWebhookSignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String webhookSecret;
    private final Duration allowedSkew;

    public PaymentWebhookSignatureVerifier(PaymentWebhookProperties paymentWebhookProperties) {
        this.webhookSecret = paymentWebhookProperties.secret();
        this.allowedSkew = Duration.ofSeconds(Math.max(paymentWebhookProperties.allowedSkewSeconds(), 0));
    }

    public void validate(String timestamp, String signature, String rawBody) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("wearhouse.pay.webhook.secret 값이 필요합니다.");
        }
        validateTimestamp(timestamp);
        String expected = signHex(timestamp + "." + rawBody);
        String normalizedSignature = normalizeSignature(signature);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                normalizedSignature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("웹훅 서명이 유효하지 않습니다.");
        }
    }

    private void validateTimestamp(String timestamp) {
        OffsetDateTime webhookTime = OffsetDateTime.parse(timestamp);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Duration delta = Duration.between(webhookTime, now).abs();
        if (delta.compareTo(allowedSkew) > 0) {
            throw new IllegalArgumentException("웹훅 타임스탬프 허용 오차를 초과했습니다.");
        }
    }

    private String normalizeSignature(String signature) {
        if (signature == null) {
            return "";
        }
        String trimmed = signature.trim();
        if (trimmed.startsWith("sha256=")) {
            return trimmed.substring("sha256=".length());
        }
        return trimmed;
    }

    private String signHex(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("웹훅 서명 검증 중 오류가 발생했습니다.", exception);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
