package com.wearhouse.order.domain.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Jacksonized
public class OrderCreateRequest {

    @NotNull
    private Long buyerId;

    @NotBlank
    private String paymentMethod;

    @NotBlank
    private String recipientName;

    @NotBlank
    private String recipientPhone;

    @NotBlank
    private String zipCode;

    @NotBlank
    private String address1;

    private String address2;

    private String deliveryRequest;

    private BigDecimal shippingFee;

    private BigDecimal discountAmount;

    private BigDecimal pointUsedAmount;

    @Builder.Default
    @NotEmpty
    @Valid
    private List<OrderCreateItemRequest> items = new ArrayList<>();

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Jacksonized
    public static class OrderCreateItemRequest {

        @NotNull
        private Long productId;

        private Long optionId;

        @NotNull
        private Long sellerId;

        @NotBlank
        private String productName;

        private String optionName;

        @NotNull
        @DecimalMin("0")
        private BigDecimal unitPrice;

        @NotNull
        @Min(1)
        private Integer quantity;
    }
}
