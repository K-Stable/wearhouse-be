package com.wearhouse.common.security.passport.gateway;

import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.common.security.jwt.JwtProvider;
import com.wearhouse.common.security.passport.gateway.dto.PassportContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.application.name", havingValue = "api-gateway")
public class GatewayJwtValidationFilter extends OncePerRequestFilter {

    public static final String PASSPORT_CONTEXT_ATTRIBUTE = "wearhouse.gateway.passport-context";

    private final GatewayPassportService gatewayPassportService;
    private final JwtProvider jwtProvider;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return GatewayRequestPolicy.isFrameworkPath(path) || GatewayRequestPolicy.isAuthOrSignupPublicPath(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        String accessToken = jwtProvider.extractAccessToken(request);

        if ((accessToken == null || accessToken.isBlank()) && GatewayRequestPolicy.isPublicBuyerPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (accessToken == null || accessToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        PassportContext passportContext;
        try {
            passportContext = gatewayPassportService.resolve(accessToken);
        } catch (RuntimeException exception) {
            writeUnauthorized(response, "ACCESS_TOKEN_INVALID", "access token 검증에 실패했습니다.");
            return;
        }

        setAuthentication(request, passportContext);
        request.setAttribute(PASSPORT_CONTEXT_ATTRIBUTE, passportContext);
        filterChain.doFilter(request, response);
    }

    private void setAuthentication(HttpServletRequest request, PassportContext passportContext) {
        LoginUser loginUser = new LoginUser(
                passportContext.userId(),
                passportContext.userType(),
                passportContext.roles(),
                passportContext.userVersion()
        );
        List<SimpleGrantedAuthority> authorities = passportContext.roles() == null
                ? List.of()
                : passportContext.roles().stream().map(SimpleGrantedAuthority::new).toList();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                loginUser,
                null,
                authorities
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void writeUnauthorized(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"data\":null}"
        );
    }
}
