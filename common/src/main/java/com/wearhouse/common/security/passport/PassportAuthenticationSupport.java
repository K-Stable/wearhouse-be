package com.wearhouse.common.security.passport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.security.current.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public final class PassportAuthenticationSupport {

    private PassportAuthenticationSupport() {
    }

    public static boolean authenticateRequest(
            ObjectMapper objectMapper,
            PassportSigner passportSigner,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        String encodedUser = request.getHeader(PassportHeaders.USER);
        String signature = request.getHeader(PassportHeaders.SIGNATURE);
        String timestamp = request.getHeader(PassportHeaders.TIMESTAMP);

        if (encodedUser == null || signature == null || timestamp == null) {
            writeUnauthorized(response, "PASSPORT_MISSING", "passport 헤더가 없습니다.");
            return false;
        }
        if (!passportSigner.verify(encodedUser, timestamp, signature)) {
            writeUnauthorized(response, "PASSPORT_INVALID", "passport 서명이 유효하지 않습니다.");
            return false;
        }

        PassportUserPayload payload;
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encodedUser);
            String json = new String(bytes, StandardCharsets.UTF_8);
            payload = objectMapper.readValue(json, PassportUserPayload.class);
        } catch (Exception exception) {
            writeUnauthorized(response, "PASSPORT_INVALID", "passport payload가 유효하지 않습니다.");
            return false;
        }

        LoginUser principal = new LoginUser(
                payload.userId(),
                payload.userType(),
                payload.roles(),
                payload.userVersion()
        );
        List<SimpleGrantedAuthority> authorities = payload.roles() == null
                ? List.of()
                : payload.roles().stream().map(SimpleGrantedAuthority::new).toList();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
        SecurityContextHolder.setContext(context);
        return true;
    }

    public static void writeUnauthorized(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"data\":null}"
        );
    }
}
