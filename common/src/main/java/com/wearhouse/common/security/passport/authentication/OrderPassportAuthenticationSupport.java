package com.wearhouse.common.security.passport.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.security.passport.PassportAuthenticationSupport;
import com.wearhouse.common.security.passport.PassportSigner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class OrderPassportAuthenticationSupport {

    private OrderPassportAuthenticationSupport() {
    }

    public static boolean authenticateRequest(
            ObjectMapper objectMapper,
            PassportSigner passportSigner,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        return PassportAuthenticationSupport.authenticateRequest(objectMapper, passportSigner, request, response);
    }
}
