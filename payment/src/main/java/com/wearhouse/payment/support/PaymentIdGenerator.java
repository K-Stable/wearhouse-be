package com.wearhouse.payment.support;

import java.security.SecureRandom;

public final class PaymentIdGenerator {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PaymentIdGenerator() {
    }

    public static String newEventId() {
        return randomBase62(26);
    }

    public static String newPaymentId() {
        return "PAY" + randomBase62(23);
    }

    private static String randomBase62(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return builder.toString();
    }
}
