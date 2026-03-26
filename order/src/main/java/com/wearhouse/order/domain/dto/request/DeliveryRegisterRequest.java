package com.wearhouse.order.domain.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DeliveryRegisterRequest(
        @NotEmpty
        List<@Valid DeliveryRegisterItemRequest> items
) {
    public record DeliveryRegisterItemRequest(
            @NotNull Long orderId,
            @NotBlank String courierCode,
            @NotBlank String invoiceNo
    ) {
    }
}
