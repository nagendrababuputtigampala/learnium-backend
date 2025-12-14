package com.learnium.learniumbackend.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.learnium.learniumbackend.entity.AuthRequest;
import com.learnium.learniumbackend.entity.AuthResponse;
import com.learnium.learniumbackend.model.User;
import com.learnium.learniumbackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public AuthResponse signIn(AuthRequest request) {
        String idToken = request.getIdToken();
        if (idToken == null || idToken.isEmpty()) {
            logger.warn("Missing or invalid idToken: {}", idToken);
            throw new IllegalArgumentException("Missing or invalid idToken");
        }
        try {
            logger.info("Verifying Firebase token");
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();
            String name = decodedToken.getName();
            logger.info("Provisioning user: {}", email);
            User user = userService.provisionUser(uid, email, name);
            logger.info("User provisioned: {} (onboardingRequired={})", email, user.isOnboardingDone());
            return new AuthResponse(user, user.isOnboardingDone());
        } catch (Exception e) {
            logger.error("Invalid Firebase token", e);
            throw new RuntimeException("Invalid Firebase token", e);
        }
    }
}
