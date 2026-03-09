package com.wearhouse.common.security.current;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class LoginUserArgumentResolverTest {

    private final LoginUserArgumentResolver resolver = new LoginUserArgumentResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportsParameterForLoginBuyerAnnotation() {
        MethodParameter parameter = methodParameter("buyerOnly", 0);

        assertTrue(resolver.supportsParameter(parameter));
    }

    @Test
    void supportsParameterForLoginSellerAnnotation() {
        MethodParameter parameter = methodParameter("sellerOnly", 0);

        assertTrue(resolver.supportsParameter(parameter));
    }

    @Test
    void supportsParameterForCurrentUserAnnotation() {
        MethodParameter parameter = methodParameter("anyLoginUser", 0);

        assertTrue(resolver.supportsParameter(parameter));
    }

    @Test
    void doesNotSupportUnsupportedType() {
        MethodParameter parameter = methodParameter("wrongType", 0);

        assertFalse(resolver.supportsParameter(parameter));
    }

    @Test
    void resolvesBuyerWhenAnnotationAndTypeMatch() {
        MethodParameter parameter = methodParameter("buyerOnly", 0);
        LoginUser loginUser = loginUser(10L, "BUYER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of())
        );

        Object resolved = resolver.resolveArgument(parameter, null, null, null);

        assertEquals(loginUser, resolved);
    }

    @Test
    void throwsForbiddenWhenBuyerAnnotationReceivesSeller() {
        MethodParameter parameter = methodParameter("buyerOnly", 0);
        LoginUser loginUser = loginUser(20L, "SELLER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of())
        );

        ErrorException exception = assertThrows(
                ErrorException.class,
                () -> resolver.resolveArgument(parameter, null, null, null)
        );

        assertEquals(CommonErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void throwsForbiddenWhenSellerAnnotationReceivesBuyer() {
        MethodParameter parameter = methodParameter("sellerOnly", 0);
        LoginUser loginUser = loginUser(30L, "BUYER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of())
        );

        ErrorException exception = assertThrows(
                ErrorException.class,
                () -> resolver.resolveArgument(parameter, null, null, null)
        );

        assertEquals(CommonErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void throwsUnauthorizedWhenAuthenticationIsMissing() {
        MethodParameter parameter = methodParameter("anyLoginUser", 0);

        ErrorException exception = assertThrows(
                ErrorException.class,
                () -> resolver.resolveArgument(parameter, null, null, null)
        );

        assertEquals(CommonErrorCode.UNAUTHORIZED, exception.errorCode());
    }

    @Test
    void throwsUnauthorizedWhenPrincipalIsNotLoginUser() {
        MethodParameter parameter = methodParameter("anyLoginUser", 0);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymous", null, List.of())
        );

        ErrorException exception = assertThrows(
                ErrorException.class,
                () -> resolver.resolveArgument(parameter, null, null, null)
        );

        assertEquals(CommonErrorCode.UNAUTHORIZED, exception.errorCode());
    }

    private MethodParameter methodParameter(String methodName, int parameterIndex) {
        try {
            Method method = TestController.class.getDeclaredMethod(methodName, methodParameterTypes(methodName));
            return new MethodParameter(method, parameterIndex);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Class<?>[] methodParameterTypes(String methodName) {
        return switch (methodName) {
            case "wrongType" -> new Class<?>[] {String.class};
            default -> new Class<?>[] {LoginUser.class};
        };
    }

    private LoginUser loginUser(Long userId, String userType) {
        return new LoginUser(userId, userType, List.of("ROLE_" + userType), 1L);
    }

    static class TestController {
        void buyerOnly(@LoginBuyer LoginUser loginUser) {
        }

        void sellerOnly(@LoginSeller LoginUser loginUser) {
        }

        void anyLoginUser(@CurrentUser LoginUser loginUser) {
        }

        void wrongType(@LoginBuyer String userId) {
        }
    }
}
