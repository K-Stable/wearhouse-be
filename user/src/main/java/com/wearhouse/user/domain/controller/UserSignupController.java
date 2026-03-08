package com.wearhouse.user.domain.controller;

import com.wearhouse.user.domain.dto.request.UserSignupRequest;
import com.wearhouse.user.domain.dto.response.UserSignupResponse;
import com.wearhouse.user.domain.service.command.UserSignupCommandService;
import com.wearhouse.user.domain.service.command.UserSignupCommandService.UserSignupSession;
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

    private final UserSignupCommandService userSignupCommandService;
    private final UserAuthCookieService userAuthCookieService;

    public UserSignupController(
            UserSignupCommandService userSignupCommandService,
            UserAuthCookieService userAuthCookieService
    ) {
        this.userSignupCommandService = userSignupCommandService;
        this.userAuthCookieService = userAuthCookieService;
    }

    @PostMapping("/buyers/signup")
    public UserSignupResponse signupBuyer(
            @Valid @RequestBody UserSignupRequest request,
            HttpServletResponse response
    ) {
        UserSignupSession session = userSignupCommandService.signupBuyer(request);
        userAuthCookieService.writeBuyerTokens(response, session.accessToken(), session.refreshToken());
        return session.response();
    }

    @PostMapping("/sellers/signup")
    public UserSignupResponse signupSeller(
            @Valid @RequestBody UserSignupRequest request,
            HttpServletResponse response
    ) {
        UserSignupSession session = userSignupCommandService.signupSeller(request);
        userAuthCookieService.writeSellerTokens(response, session.accessToken(), session.refreshToken());
        return session.response();
    }
}
