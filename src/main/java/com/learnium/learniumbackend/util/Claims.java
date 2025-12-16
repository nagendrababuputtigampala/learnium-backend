package com.learnium.learniumbackend.util;

import com.google.firebase.auth.FirebaseToken;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Claims {

    public static Map<String, Object> getClaimsFromToken() {
        Map<String, Object> claims = new HashMap<>();
        Authentication authentication =SecurityContextHolder.getContext().getAuthentication();
        if(ObjectUtils.isNotEmpty(authentication) && ObjectUtils.isNotEmpty(authentication.getPrincipal())) {
            if(authentication.getPrincipal() instanceof FirebaseToken) {
                return ((FirebaseToken) authentication.getPrincipal()).getClaims();

            }
            return (Map<String, Object>) authentication.getPrincipal();
        }
        return new HashMap<>();
    }


}
