package com.wearhouse.user.domain.service.command;

import com.wearhouse.user.domain.dto.request.UserSignupRequest;
import com.wearhouse.user.domain.dto.response.UserSignupResponse;
import com.wearhouse.user.infra.auth.AuthSignupClient;
import com.wearhouse.user.infra.auth.dto.AuthInternalSignupResponse;
import org.springframework.stereotype.Service;

@Service
public class UserSignupCommandService {

    private final AuthSignupClient authSignupClient;

    public UserSignupCommandService(AuthSignupClient authSignupClient) {
        this.authSignupClient = authSignupClient;
    }

    public UserSignupSession signupBuyer(UserSignupRequest request) {
        AuthInternalSignupResponse response = authSignupClient.signupBuyer(
                request.email(),
                request.password(),
                request.displayName()
        );
        return toSession(response);
    }

    public UserSignupSession signupSeller(UserSignupRequest request) {
        AuthInternalSignupResponse response = authSignupClient.signupSeller(
                request.email(),
                request.password(),
                request.displayName()
        );
        return toSession(response);
    }

    private UserSignupSession toSession(AuthInternalSignupResponse response) {
        UserSignupResponse payload = new UserSignupResponse(
                response.userId(),
                response.userType(),
                response.email(),
                response.accessTokenExpiresAt()
        );
        return new UserSignupSession(payload, response.accessToken(), response.refreshToken());
    }

    public record UserSignupSession(
            UserSignupResponse response,
            String accessToken,
            String refreshToken
    ) {
    }
}
