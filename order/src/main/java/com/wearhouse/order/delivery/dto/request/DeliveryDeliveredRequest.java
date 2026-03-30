package com.wearhouse.order.delivery.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DeliveryDeliveredRequest(
        @NotEmpty
        List<@NotNull Long> orderIds
) {
}
