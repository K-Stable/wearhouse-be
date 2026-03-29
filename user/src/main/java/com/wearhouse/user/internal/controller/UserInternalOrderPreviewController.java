package com.wearhouse.user.internal.controller;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.user.domain.dto.request.InternalBuyerOrderPreviewRequest;
import com.wearhouse.user.domain.dto.response.InternalBuyerOrderPreviewResponse;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.internal.service.UserInternalOrderPreviewQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/users/order-preview")
@RequiredArgsConstructor
public class UserInternalOrderPreviewController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final UserInternalOrderPreviewQueryService userInternalOrderPreviewQueryService;

    @Value("${wearhouse.user.internal.shared-secret:wearhouse-user-internal-secret}")
    private String internalSharedSecret;

    @PostMapping("/buyers")
    public ApiResponse<InternalBuyerOrderPreviewResponse> getBuyerPreviewInfo(
            @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String headerSecret,
            @Valid @RequestBody InternalBuyerOrderPreviewRequest request
    ) {
        requireInternalSecret(headerSecret);
        InternalBuyerOrderPreviewResponse response = userInternalOrderPreviewQueryService.getBuyerPreviewInfo(request.buyerId());
        return ApiResponse.success(response);
    }

    private void requireInternalSecret(String headerSecret) {
        if (!internalSharedSecret.equals(headerSecret)) {
            throw new ErrorException(UserErrorCode.INTERNAL_SECRET_INVALID);
        }
    }
}
