package com.wearhouse.user.domain.controller;

import com.wearhouse.user.domain.dto.request.BuyerSignupRequest;
import com.wearhouse.user.domain.dto.request.UserSignupRequest;
import com.wearhouse.user.domain.dto.response.UserSignupResponse;
import com.wearhouse.user.domain.service.UserAuthService;
import com.wearhouse.user.domain.service.UserAuthService.UserSignupSession;
import com.wearhouse.user.support.cookie.UserAuthCookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/buyer")
@RequiredArgsConstructor
@Validated
@Slf4j
public class BuyerController {

    private final UserAuthService userAuthService;
    private final UserAuthCookieService userAuthCookieService;

    @PostMapping("/buyers/signup")
    public UserSignupResponse signupBuyer(
            @Valid @RequestBody BuyerSignupRequest request,
            HttpServletResponse response
    ) {
        UserSignupSession session = userAuthService.signupBuyer(request);
        userAuthCookieService.writeBuyerTokens(response, session.accessToken(), session.refreshToken());
        return session.response();
    }

    @PostMapping("/sellers/signup")
    public UserSignupResponse signupSeller(
            @Valid @RequestBody UserSignupRequest request,
            HttpServletResponse response
    ) {
        UserSignupSession session = userAuthService.signupSeller(request);
        userAuthCookieService.writeSellerTokens(response, session.accessToken(), session.refreshToken());
        return session.response();
    }
}
