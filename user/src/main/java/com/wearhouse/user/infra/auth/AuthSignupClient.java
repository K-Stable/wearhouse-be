package com.wearhouse.user.infra.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.user.infra.auth.dto.AuthInternalSignupRequest;
import com.wearhouse.user.infra.auth.dto.AuthInternalSignupResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AuthSignupClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String buyerSignupUrl;
    private final String sellerSignupUrl;
    private final String internalSharedSecret;

    public AuthSignupClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${wearhouse.user.auth.buyer-signup-url}") String buyerSignupUrl,
            @Value("${wearhouse.user.auth.seller-signup-url}") String sellerSignupUrl,
            @Value("${wearhouse.user.auth.internal-shared-secret}") String internalSharedSecret
    ) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.buyerSignupUrl = buyerSignupUrl;
        this.sellerSignupUrl = sellerSignupUrl;
        this.internalSharedSecret = internalSharedSecret;
    }

    public AuthInternalSignupResponse signupBuyer(String email, String password, String displayName) {
        return signup(buyerSignupUrl, email, password, displayName);
    }

    public AuthInternalSignupResponse signupSeller(String email, String password, String displayName) {
        return signup(sellerSignupUrl, email, password, displayName);
    }

    private AuthInternalSignupResponse signup(String url, String email, String password, String displayName) {
        JsonNode root = restClient.post()
                .uri(url)
                .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                .body(new AuthInternalSignupRequest(email, password, displayName))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalStateException("auth-service signup 호출 실패: " + response.getStatusCode());
                })
                .body(JsonNode.class);

        if (root == null || !root.path("success").asBoolean(false) || root.path("data").isMissingNode()) {
            throw new IllegalStateException("auth-service signup 응답이 유효하지 않습니다.");
        }

        return objectMapper.convertValue(root.path("data"), AuthInternalSignupResponse.class);
    }
}
