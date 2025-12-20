package com.learnium.learniumbackend.config;

import com.google.firebase.auth.FirebaseToken;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUserProvider {

    public FirebaseToken getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("Unauthenticated request");
        }
        if(ObjectUtils.isNotEmpty(auth.getPrincipal()) && (auth.getPrincipal() instanceof FirebaseToken)) {
            return (FirebaseToken)auth.getPrincipal();
        }
        return null;
    }
}