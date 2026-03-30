package com.wearhouse.payment.domain.payment.model;

public enum PaymentMethod {
    CARD,
    STABLE;

    private static final String LEGACY_STABLEPAY = "STABLEPAY";

    public static PaymentMethod from(String rawMethod) {
        String token = requestedTokenOrDefault(rawMethod);
        if (STABLE.name().equals(token) || LEGACY_STABLEPAY.equals(token)) {
            return STABLE;
        }
        return CARD;
    }

    public static boolean isStable(String rawMethod) {
        return from(rawMethod) == STABLE;
    }

    public static String requestedTokenOrDefault(String rawMethod) {
        if (rawMethod == null || rawMethod.isBlank()) {
            return CARD.name();
        }
        return rawMethod.trim().toUpperCase();
    }
}
