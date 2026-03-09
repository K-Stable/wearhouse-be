package com.wearhouse.user.domain.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SellerSignupRequest(

        @NotBlank
        String logingId,

        @NotBlank
        String password,

        @NotBlank
        String name,

        @Email @NotBlank
        String email,

        @NotBlank
        String sellerNo,

        @NotBlank
        String phone
) {
}
