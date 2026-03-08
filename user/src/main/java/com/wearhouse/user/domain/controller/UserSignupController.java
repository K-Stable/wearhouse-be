package com.wearhouse.user.domain.controller;

import com.wearhouse.user.domain.dto.request.UserSignupRequest;
import com.wearhouse.user.domain.dto.response.UserSignupResponse;
import com.wearhouse.user.domain.service.UserAuthService;
import com.wearhouse.user.domain.service.UserAuthService.UserSignupSession;
import com.wearhouse.user.support.cookie.UserAuthCookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserSignupController {

    private final UserAuthService userAuthService;
    private final UserAuthCookieService userAuthCookieService;

    public UserSignupController(
            UserAuthService userAuthService,
            UserAuthCookieService userAuthCookieService
    ) {
        this.userAuthService = userAuthService;
        this.userAuthCookieService = userAuthCookieService;
    }

    @PostMapping("/buyers/signup")
    public UserSignupResponse signupBuyer(
            @Valid @RequestBody UserSignupRequest request,
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
