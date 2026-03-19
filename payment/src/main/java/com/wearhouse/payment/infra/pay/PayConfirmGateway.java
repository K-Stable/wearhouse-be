package com.wearhouse.payment.infra.pay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.payment.domain.payment.dto.request.PaymentConfirmRequest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PayConfirmGateway {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String accessKey;
    private final String secretKey;
    private final String confirmPath;

    public PayConfirmGateway(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${wearhouse.pay.api-base-url:http://localhost:8090}") String apiBaseUrl,
            @Value("${wearhouse.pay.access-key:pay_access_key}") String accessKey,
            @Value("${wearhouse.pay.secret-key:pay_secret_key}") String secretKey,
            @Value("${wearhouse.pay.confirm-path:/v1/payments/confirm}") String confirmPath
    ) {
        this.restClient = restClientBuilder.baseUrl(apiBaseUrl).build();
        this.objectMapper = objectMapper;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.confirmPath = confirmPath;
    }

    public PayConfirmResult confirm(PaymentConfirmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentKey", request.paymentKey());
        body.put("amount", toAmountString(request.amount()));
        body.put("orderId", String.valueOf(request.orderId()));

        String responseBody = restClient.post()
                .uri(confirmPath)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Merchant-Access-Key", accessKey)
                .header("X-Merchant-Secret-Key", secretKey)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(responseBody == null ? "{}" : responseBody);
            JsonNode node = root.path("data").isMissingNode() ? root : root.path("data");
            String paymentId = text(node, "paymentId");
            String status = normalizeStatus(text(node, "status"));
            String commandStatus = normalizeStatus(text(node, "commandStatus"));
            String commandId = text(node, "commandId");
            String txHash = text(node, "txHash");
            String reasonCode = text(node, "reasonCode");

            if (isAuthorized(status, commandStatus)) {
                return PayConfirmResult.authorized(paymentId, commandId, commandStatus, txHash);
            }
            if (isFailed(status, commandStatus)) {
                return PayConfirmResult.failed(paymentId, commandId, commandStatus, reasonCode);
            }
            return PayConfirmResult.pending(paymentId, commandId, commandStatus);
        } catch (Exception exception) {
            throw new IllegalStateException("Pay confirm 응답 파싱에 실패했습니다.", exception);
        }
    }

    private boolean isAuthorized(String status, String commandStatus) {
        return "authorized".equals(status)
                || "captured".equals(status)
                || "settled".equals(status)
                || "authorized_confirmed".equals(commandStatus)
                || "succeeded".equals(commandStatus);
    }

    private boolean isFailed(String status, String commandStatus) {
        return "failed".equals(status)
                || "failed".equals(commandStatus)
                || "dead_letter".equals(commandStatus)
                || "payment_failed".equals(status);
    }

    private String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText(null);
        return (value == null || value.isBlank()) ? null : value;
    }

    private String toAmountString(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    public record PayConfirmResult(
            String paymentId,
            String commandId,
            String commandStatus,
            String txHash,
            String reasonCode,
            ResultType resultType
    ) {
        public static PayConfirmResult authorized(String paymentId, String commandId, String commandStatus, String txHash) {
            return new PayConfirmResult(paymentId, commandId, commandStatus, txHash, null, ResultType.AUTHORIZED);
        }

        public static PayConfirmResult failed(String paymentId, String commandId, String commandStatus, String reasonCode) {
            return new PayConfirmResult(paymentId, commandId, commandStatus, null, reasonCode, ResultType.FAILED);
        }

        public static PayConfirmResult pending(String paymentId, String commandId, String commandStatus) {
            return new PayConfirmResult(paymentId, commandId, commandStatus, null, null, ResultType.PENDING);
        }
    }

    public enum ResultType {
        AUTHORIZED,
        FAILED,
        PENDING
    }
}
