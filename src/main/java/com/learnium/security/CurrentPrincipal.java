package com.learnium.security;

import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Accessor for the {@link LearniumPrincipal} of the current request. */
public final class CurrentPrincipal {

    private CurrentPrincipal() {}

    public static LearniumPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LearniumPrincipal principal) {
            return principal;
        }
        throw new BusinessException(ErrorCode.AUTH_UNAUTHENTICATED);
    }
}
