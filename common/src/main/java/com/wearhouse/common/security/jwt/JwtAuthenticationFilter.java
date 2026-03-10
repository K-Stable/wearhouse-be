package com.wearhouse.common.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.common.security.jwt.exception.JwtErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String accessToken = jwtProvider.extractAccessToken(request);
        if (accessToken == null || accessToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtProvider.DecodedAccessToken decoded = jwtProvider.decodeAccessToken(accessToken);
            LoginUser loginUser = new LoginUser(
                    decoded.userId(),
                    decoded.userType(),
                    decoded.roles(),
                    decoded.userVersion()
            );
            List<SimpleGrantedAuthority> authorities = decoded.roles() == null
                    ? List.of()
                    : decoded.roles().stream().map(SimpleGrantedAuthority::new).toList();
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    loginUser,
                    null,
                    authorities
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(JwtErrorCode.ACCESS_TOKEN_INVALID.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), ApiResponse.failure(JwtErrorCode.ACCESS_TOKEN_INVALID));
        }
    }
}
