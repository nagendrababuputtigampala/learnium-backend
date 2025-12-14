package com.learnium.learniumbackend.controller;

import com.learnium.learniumbackend.entity.AuthRequest;
import com.learnium.learniumbackend.entity.AuthResponse;
import com.learnium.learniumbackend.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signIn(
            @RequestBody(required = false) AuthRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {
        String idToken = (request != null && request.getIdToken() != null && !request.getIdToken().isEmpty())
                ? request.getIdToken()
                : authorizationHeader.startsWith("Bearer ") ? authorizationHeader.substring(7) : null;
        if (idToken == null || idToken.isEmpty()) {
            throw new IllegalArgumentException("Missing Firebase ID token in request body or Authorization header");
        }
        AuthResponse response = authService.signIn(new AuthRequest(idToken));
        logger.info("Login API success for user: {}", response.getUserProfile().getEmail());
        return ResponseEntity.ok(response);
    }
}
