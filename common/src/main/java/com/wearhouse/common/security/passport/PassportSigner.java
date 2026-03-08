package com.wearhouse.common.security.passport;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class PassportSigner {

    private final String sharedSecret;

    public PassportSigner(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public String sign(String encodedUser, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((encodedUser + "." + timestamp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Passport 서명 생성에 실패했습니다.", exception);
        }
    }

    public boolean verify(String encodedUser, String timestamp, String signature) {
        String expected = sign(encodedUser, timestamp);
        return constantTimeEquals(expected, signature);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        if (expected.length() != actual.length()) {
            return false;
        }
        int result = 0;
        for (int index = 0; index < expected.length(); index++) {
            result |= expected.charAt(index) ^ actual.charAt(index);
        }
        return result == 0;
    }
}
