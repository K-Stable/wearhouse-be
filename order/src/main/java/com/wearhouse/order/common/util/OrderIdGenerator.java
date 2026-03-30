package com.wearhouse.order.common.util;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class OrderIdGenerator {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter ORDER_NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private OrderIdGenerator() {
    }

    public static String newOrderNo() {
        return "ORD" + LocalDateTime.now().format(ORDER_NO_TIME_FORMAT) + randomBase62(6);
    }

    public static String newEventId() {
        return randomBase62(26);
    }

    public static String newSagaId() {
        return randomBase62(26);
    }

    private static String randomBase62(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            int randomIndex = RANDOM.nextInt(BASE62.length());
            builder.append(BASE62.charAt(randomIndex));
        }
        return builder.toString();
    }
}
