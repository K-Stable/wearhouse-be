package com.wearhouse.payment.infra.pay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.payment.domain.payment.dto.request.PaymentPrepareRequest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PayPrepareGateway {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String accessKey;
    private final String secretKey;
    private final String preparePath;

    public PayPrepareGateway(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${wearhouse.pay.api-base-url:http://localhost:8090}") String apiBaseUrl,
            @Value("${wearhouse.pay.access-key:pay_access_key}") String accessKey,
            @Value("${wearhouse.pay.secret-key:pay_secret_key}") String secretKey,
            @Value("${wearhouse.pay.prepare-path:/api/v1/payments/checkout/prepare}") String preparePath
    ) {
        this.restClient = restClientBuilder.baseUrl(apiBaseUrl).build();
        this.objectMapper = objectMapper;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.preparePath = preparePath;
    }

    public PayPrepareResult prepare(PaymentPrepareRequest request, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", String.valueOf(request.orderId()));
        body.put("customerKey", request.customerId());
        body.put("orderName", request.orderName());
        body.put("amount", toAmountInt(request.amount()));
        body.put("successUrl", request.successUrl());
        body.put("failUrl", request.failUrl());

        RestClient.RequestBodySpec requestSpec = restClient.post()
                .uri(preparePath)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Merchant-Access-Key", accessKey)
                .header("X-Merchant-Secret-Key", secretKey);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            requestSpec.header("Idempotency-Key", idempotencyKey);
        }

        String responseBody = requestSpec
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(responseBody == null ? "{}" : responseBody);
            JsonNode node = root.path("data").isMissingNode() ? root : root.path("data");

            return new PayPrepareResult(
                    text(node, "checkoutSessionId"),
                    text(node, "checkoutUrl"),
                    text(node, "appLaunchUrl"),
                    normalizeStatus(text(node, "paymentStatus")),
                    text(node, "checkoutExpiresAt"),
                    text(node, "paymentKey"),
                    text(node, "merchantKey"),
                    text(node, "nonce"),
                    text(node, "deadline"),
                    text(node, "payloadHash"),
                    text(node, "reasonCode"),
                    text(node, "reasonMessage"),
                    text(node, "commandStatus")
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Pay prepare 응답 파싱에 실패했습니다.", exception);
        }
    }

    private String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "READY";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText(null);
        return (value == null || value.isBlank()) ? null : value;
    }

    private int toAmountInt(BigDecimal amount) {
        return amount.intValueExact();
    }

    public record PayPrepareResult(
            String checkoutSessionId,
            String checkoutUrl,
            String appLaunchUrl,
            String paymentStatus,
            String checkoutExpiresAt,
            String paymentKey,
            String merchantKey,
            String nonce,
            String deadline,
            String payloadHash,
            String reasonCode,
            String reasonMessage,
            String commandStatus
    ) {
    }
}
