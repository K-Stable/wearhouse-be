package com.wearhouse.auth.domain.dto.request;

import jakarta.validation.constraints.NotBlank;

public record InternalValidateRequest(
        @NotBlank String accessToken
) {
}
