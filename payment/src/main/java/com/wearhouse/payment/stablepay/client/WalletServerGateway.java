package com.wearhouse.payment.stablepay.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.payment.support.config.PaymentPayProperties;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class WalletServerGateway {

    private static final int DEFAULT_TIMEOUT_STATUS = 408;
    private static final int DEFAULT_FAILURE_STATUS = 500;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String secretKey;
    private final String preparePath;
    private final String confirmPath;

    public WalletServerGateway(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            PaymentPayProperties paymentPayProperties
    ) {
        String apiBaseUrl = paymentPayProperties.apiBaseUrl();
        String secretKey = paymentPayProperties.secretKey();
        String preparePath = paymentPayProperties.preparePath();
        String confirmPath = paymentPayProperties.confirmPath();
        long timeoutMs = paymentPayProperties.timeoutMs();
        long normalizedTimeoutMs = Math.max(1000L, timeoutMs);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(normalizedTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(normalizedTimeoutMs));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(sanitizeBaseUrl(apiBaseUrl))
                .build();
        this.objectMapper = objectMapper;
        this.secretKey = secretKey;
        this.preparePath = preparePath;
        this.confirmPath = confirmPath;
    }

    public WalletPrepareResult walletPrepare(WalletPrepareRequest request, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", requireOrderNo(request.orderNo()));
        body.put("orderName", request.orderName());
        body.put("amount", toAmountInt(request.amount()));
        body.put("successUrl", request.successUrl());
        body.put("failUrl", request.failUrl());

        String raw = executePost(preparePath, body, idempotencyKey, "prepare");
        JsonNode node = extractDataNode(raw, "prepare");
        return new WalletPrepareResult(
                text(node, "checkoutSessionId"),
                text(node, "checkoutUrl"),
                text(node, "appLaunchUrl"),
                text(node, "checkoutExpiresAt"),
                normalizeStatusUpper(text(node, "paymentStatus"))
        );
    }

    public WalletConfirmResult walletConfirm(WalletConfirmRequest request, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentKey", request.paymentKey());
        body.put("amount", toAmountInt(request.amount()));
        body.put("orderId", requireOrderNo(request.orderNo()));

        String raw = executePost(confirmPath, body, idempotencyKey, "confirm");
        JsonNode node = extractDataNode(raw, "confirm");

        boolean confirmed = node.path("confirmed").asBoolean(false);
        String paymentId = text(node, "paymentId");
        String status = normalizeStatusLower(text(node, "status"));
        String commandStatus = normalizeStatusLower(text(node, "commandStatus"));
        String commandId = text(node, "commandId");
        String txHash = text(node, "txHash");
        String reasonCode = text(node, "reasonCode");

        if (confirmed) {
            return WalletConfirmResult.authorized(paymentId, commandId, commandStatus, txHash);
        }
        if (isAuthorized(status, commandStatus)) {
            return WalletConfirmResult.authorized(paymentId, commandId, commandStatus, txHash);
        }
        if (isFailed(status, commandStatus)) {
            return WalletConfirmResult.failed(paymentId, commandId, commandStatus, reasonCode);
        }
        return WalletConfirmResult.pending(paymentId, commandId, commandStatus);
    }

    private String executePost(String path, Map<String, Object> body, String idempotencyKey, String phase) {
        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-secret-key", secretKey);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                spec.header("idempotency-key", idempotencyKey);
            }
            String raw = spec.body(body).retrieve().body(String.class);
            validateSuccessEnvelope(raw, phase);
            return raw;
        } catch (RestClientResponseException exception) {
            throw toApiException(
                    exception.getStatusCode().value(),
                    exception.getResponseBodyAsString(),
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new WalletServerApiException(
                    "Wallet API request timed out.",
                    DEFAULT_TIMEOUT_STATUS,
                    null,
                    null,
                    exception
            );
        } catch (RestClientException exception) {
            throw new WalletServerApiException(
                    exception.getMessage(),
                    DEFAULT_FAILURE_STATUS,
                    null,
                    null,
                    exception
            );
        }
    }

    private void validateSuccessEnvelope(String raw, String phase) {
        JsonNode root = readJson(raw);
        JsonNode successNode = root.path("success");
        if (!successNode.isMissingNode() && !successNode.isNull() && !successNode.asBoolean(true)) {
            String message = text(root, "message");
            String code = text(root, "code");
            throw new WalletServerApiException(
                    message == null ? "Wallet API " + phase + " failed." : message,
                    DEFAULT_FAILURE_STATUS,
                    code,
                    root,
                    null
            );
        }
    }

    private WalletServerApiException toApiException(int status, String rawBody, Exception cause) {
        JsonNode root = readJson(rawBody);
        JsonNode errorNode = root.path("error");
        if (!errorNode.isMissingNode() && errorNode.isObject()) {
            String message = text(errorNode, "message");
            String code = text(errorNode, "code");
            return new WalletServerApiException(
                    message == null ? "Wallet API request failed: " + status : message,
                    status,
                    code,
                    root,
                    cause
            );
        }
        return new WalletServerApiException(
                "Wallet API request failed: " + status,
                status,
                null,
                root,
                cause
        );
    }

    private JsonNode extractDataNode(String raw, String phase) {
        JsonNode root = readJson(raw);
        if (root.has("data")) {
            JsonNode dataNode = root.get("data");
            if (dataNode == null || dataNode.isNull()) {
                throw new WalletServerApiException(
                        "Wallet API returned an empty data payload.",
                        DEFAULT_FAILURE_STATUS,
                        text(root, "code"),
                        root,
                        null
                );
            }
            return dataNode;
        }
        if (!root.isObject()) {
            throw new WalletServerApiException(
                    "Wallet API " + phase + " response parsing failed.",
                    DEFAULT_FAILURE_STATUS,
                    null,
                    root,
                    null
            );
        }
        return root;
    }

    private JsonNode readJson(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(raw);
        } catch (Exception exception) {
            throw new WalletServerApiException(
                    "Wallet API response parsing failed.",
                    DEFAULT_FAILURE_STATUS,
                    null,
                    raw,
                    exception
            );
        }
    }

    private String sanitizeBaseUrl(String raw) {
        return raw == null ? "" : raw.replaceAll("/+$", "");
    }

    private String normalizeStatusUpper(String raw) {
        if (raw == null || raw.isBlank()) {
            return "READY";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStatusLower(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
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

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode valueNode = node.path(field);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private String requireOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("orderNo 값이 필요합니다.");
        }
        return orderNo;
    }

    private int toAmountInt(BigDecimal amount) {
        return amount.intValueExact();
    }

    public record WalletPrepareRequest(
            String orderNo,
            String orderName,
            BigDecimal amount,
            String successUrl,
            String failUrl
    ) {
    }

    public record WalletConfirmRequest(
            String orderNo,
            String paymentKey,
            BigDecimal amount
    ) {
    }

    public record WalletPrepareResult(
            String checkoutSessionId,
            String checkoutUrl,
            String appLaunchUrl,
            String checkoutExpiresAt,
            String paymentStatus
    ) {
    }

    public record WalletConfirmResult(
            String paymentId,
            String commandId,
            String commandStatus,
            String txHash,
            String reasonCode,
            ResultType resultType
    ) {
        public static WalletConfirmResult authorized(String paymentId, String commandId, String commandStatus, String txHash) {
            return new WalletConfirmResult(paymentId, commandId, commandStatus, txHash, null, ResultType.AUTHORIZED);
        }

        public static WalletConfirmResult failed(String paymentId, String commandId, String commandStatus, String reasonCode) {
            return new WalletConfirmResult(paymentId, commandId, commandStatus, null, reasonCode, ResultType.FAILED);
        }

        public static WalletConfirmResult pending(String paymentId, String commandId, String commandStatus) {
            return new WalletConfirmResult(paymentId, commandId, commandStatus, null, null, ResultType.PENDING);
        }
    }

    public enum ResultType {
        AUTHORIZED,
        FAILED,
        PENDING
    }

    public static class WalletServerApiException extends RuntimeException {
        private final int status;
        private final String code;
        private final Object details;

        public WalletServerApiException(String message, int status, String code, Object details, Throwable cause) {
            super(message, cause);
            this.status = status;
            this.code = code;
            this.details = details;
        }

        public int getStatus() {
            return status;
        }

        public String getCode() {
            return code;
        }

        public Object getDetails() {
            return details;
        }
    }
}
