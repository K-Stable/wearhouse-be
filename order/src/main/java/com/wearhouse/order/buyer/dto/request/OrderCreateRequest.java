package com.wearhouse.order.buyer.dto.request;

import com.wearhouse.order.domain.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
public record OrderCreateRequest(
        Long buyerId,
        @NotNull PaymentMethod paymentMethod,
        @NotBlank String recipientName,
        @NotBlank String recipientPhone,
        @NotBlank String zipCode,
        @NotBlank String address1,
        String address2,
        String deliveryRequest,
        BigDecimal shippingFee,
        BigDecimal discountAmount,
        BigDecimal pointUsedAmount,
        @NotEmpty @Valid List<OrderCreateItemRequest> items
) {

    @Builder
    @Jacksonized
    public record OrderCreateItemRequest(
            @NotNull Long productId,
            Long optionId,
            @NotBlank String productName,
            String optionName,
            @NotNull @DecimalMin("0") BigDecimal unitPrice,
            @NotNull @Min(1) Integer quantity
    ) {
    }
}
