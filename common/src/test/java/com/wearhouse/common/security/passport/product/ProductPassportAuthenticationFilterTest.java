package com.wearhouse.common.security.passport.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.security.passport.PassportHeaders;
import com.wearhouse.common.security.passport.PassportSigner;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class ProductPassportAuthenticationFilterTest {

    private static final String SHARED_SECRET = "test-shared-secret";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PassportSigner passportSigner = new PassportSigner(SHARED_SECRET);
    private final ProductPassportAuthenticationFilter filter =
            new ProductPassportAuthenticationFilter(objectMapper, SHARED_SECRET);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsVerifiedHeaderForSellerSeasonListRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/seller/products/seasons");
        MockHttpServletResponse response = new MockHttpServletResponse();
        applyPassportHeaders(request, 1L, "SELLER", List.of("ROLE_SELLER"), 1L);

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("true", response.getHeader(PassportHeaders.VERIFIED));
    }

    @Test
    void doesNotSetVerifiedHeaderForOtherSellerProductRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/seller/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        applyPassportHeaders(request, 1L, "SELLER", List.of("ROLE_SELLER"), 1L);

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(response.getHeader(PassportHeaders.VERIFIED));
    }

    private void applyPassportHeaders(
            MockHttpServletRequest request,
            Long userId,
            String userType,
            List<String> roles,
            Long userVersion
    ) throws Exception {
        ProductPassportPayload payload = new ProductPassportPayload(userId, userType, roles, userVersion);
        String payloadJson = objectMapper.writeValueAsString(payload);
        String encodedUser = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String signature = passportSigner.sign(encodedUser, timestamp);

        request.addHeader(PassportHeaders.USER, encodedUser);
        request.addHeader(PassportHeaders.TIMESTAMP, timestamp);
        request.addHeader(PassportHeaders.SIGNATURE, signature);
    }
}
