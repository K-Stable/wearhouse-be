package com.wearhouse.product.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum Category {
    OUTER,
    TOP,
    BOTTOM,
    DRESS,
    SHOES,
    ACCESSORY;

    @JsonCreator
    public static Category from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("category 값이 비어 있습니다.");
        }
        return Category.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
