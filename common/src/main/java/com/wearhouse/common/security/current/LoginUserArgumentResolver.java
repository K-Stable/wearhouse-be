package com.wearhouse.common.security.current;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return hasLoginAnnotation(parameter)
                && LoginUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ErrorException(CommonErrorCode.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof LoginUser loginUser)) {
            throw new ErrorException(CommonErrorCode.UNAUTHORIZED);
        }

        if (parameter.hasParameterAnnotation(LoginBuyer.class) && !loginUser.isBuyer()) {
            throw new ErrorException(CommonErrorCode.FORBIDDEN);
        }
        if (parameter.hasParameterAnnotation(LoginSeller.class) && !loginUser.isSeller()) {
            throw new ErrorException(CommonErrorCode.FORBIDDEN);
        }
        return loginUser;
    }

    private boolean hasLoginAnnotation(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                || parameter.hasParameterAnnotation(LoginBuyer.class)
                || parameter.hasParameterAnnotation(LoginSeller.class);
    }
}
