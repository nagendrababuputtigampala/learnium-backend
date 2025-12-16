package com.learnium.learniumbackend.service;

import com.learnium.learniumbackend.entity.request.AuthRequest;
import com.learnium.learniumbackend.entity.response.AuthResponse;
import com.learnium.learniumbackend.model.User;
import com.learnium.learniumbackend.repository.UserRepository;
import com.learnium.learniumbackend.util.Claims;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static com.learnium.learniumbackend.util.Constants.DEFAULT_GRADE;

@Service
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final UserService userService;

    public AuthService(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Transactional
    public AuthResponse signIn(AuthRequest authRequest) {
        Map<String, Object> claims = Claims.getClaimsFromToken();
        try {
            logger.info("Verifying Firebase token");
            String uid = String.valueOf(claims.get("user_id"));
            String email = String.valueOf(claims.getOrDefault("email", ObjectUtils.isNotEmpty(authRequest) ? authRequest.getEmail() : StringUtils.EMPTY));
            String name = String.valueOf(claims.getOrDefault("name", ObjectUtils.isNotEmpty(authRequest) ? authRequest.getDisplayName() : StringUtils.EMPTY));
            Integer grade = ObjectUtils.isNotEmpty(authRequest) && ObjectUtils.isNotEmpty(authRequest.getGradeLevel()) ? authRequest.getGradeLevel() : DEFAULT_GRADE;
            logger.info("Provisioning user: {}", email);
            User user = userService.provisionUser(uid, email, name, grade);
            logger.info("User provisioned: {} (onboardingRequired={})", email, user.isOnboardingDone());
            return new AuthResponse(user, user.isOnboardingDone());
        } catch (Exception e) {
            logger.error("Invalid Firebase token", e);
            throw new RuntimeException("Invalid Firebase token", e);
        }
    }
}
