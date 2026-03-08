package com.wearhouse.apigateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.apigateway.security.dto.AuthValidateRequest;
import com.wearhouse.apigateway.security.dto.AuthValidateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AuthValidateClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String validateUrl;
    private final String internalSharedSecret;

    public AuthValidateClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${wearhouse.gateway.auth.validate-url}") String validateUrl,
            @Value("${wearhouse.gateway.auth.internal-shared-secret}") String internalSharedSecret
    ) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.validateUrl = validateUrl;
        this.internalSharedSecret = internalSharedSecret;
    }

    public AuthValidateResponse validate(String accessToken) {
        JsonNode root = restClient.post()
                .uri(validateUrl)
                .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                .body(new AuthValidateRequest(accessToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalStateException("auth-service validate 호출 실패: " + response.getStatusCode());
                })
                .body(JsonNode.class);

        if (root == null || root.path("success").asBoolean(false) == false || root.path("data").isMissingNode()) {
            throw new IllegalStateException("auth-service validate 응답이 유효하지 않습니다.");
        }

        return objectMapper.convertValue(root.path("data"), AuthValidateResponse.class);
    }
}
