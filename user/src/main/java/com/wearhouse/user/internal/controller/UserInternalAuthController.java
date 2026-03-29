package com.wearhouse.user.internal.controller;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.user.domain.dto.request.InternalUserAuthByIdRequest;
import com.wearhouse.user.domain.dto.request.InternalUserAuthByLoginIdRequest;
import com.wearhouse.user.domain.dto.response.InternalUserAuthAccountResponse;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.domain.model.UserAuthAccount;
import com.wearhouse.user.domain.model.UserType;
import com.wearhouse.user.internal.service.UserInternalAuthQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/users/auth")
@RequiredArgsConstructor
public class UserInternalAuthController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final UserInternalAuthQueryService userInternalAuthQueryService;

    @Value("${wearhouse.user.internal.shared-secret}")
    private String internalSharedSecret;

    @PostMapping("/by-login-id")
    public ApiResponse<InternalUserAuthAccountResponse> findByLoginId(
            @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String headerSecret,
            @Valid @RequestBody InternalUserAuthByLoginIdRequest request
    ) {
        requireInternalSecret(headerSecret);
        UserAuthAccount account = userInternalAuthQueryService.findByLoginId(
                parseUserType(request.userType()),
                request.loginId()
        );
        return ApiResponse.success(toResponse(account));
    }

    @PostMapping("/by-id")
    public ApiResponse<InternalUserAuthAccountResponse> findById(
            @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String headerSecret,
            @Valid @RequestBody InternalUserAuthByIdRequest request
    ) {
        requireInternalSecret(headerSecret);
        UserAuthAccount account = userInternalAuthQueryService.findById(
                parseUserType(request.userType()),
                request.userId()
        );
        return ApiResponse.success(toResponse(account));
    }

    private void requireInternalSecret(String headerSecret) {
        if (!internalSharedSecret.equals(headerSecret)) {
            throw new ErrorException(UserErrorCode.INTERNAL_SECRET_INVALID);
        }
    }

    private UserType parseUserType(String raw) {
        try {
            return UserType.valueOf(raw);
        } catch (Exception exception) {
            throw new ErrorException(UserErrorCode.USER_TYPE_INVALID);
        }
    }

    private InternalUserAuthAccountResponse toResponse(UserAuthAccount account) {
        return new InternalUserAuthAccountResponse(
                account.userId(),
                account.userType().name(),
                account.email(),
                account.passwordHash(),
                account.status(),
                account.userVersion()
        );
    }
}
