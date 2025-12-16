package com.learnium.learniumbackend.controller;

import com.learnium.learniumbackend.entity.request.AuthRequest;
import com.learnium.learniumbackend.entity.response.AuthResponse;
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
            @RequestBody(required = false) AuthRequest authRequest) {
        AuthResponse response = authService.signIn(authRequest);
        logger.info("Login API success for user: {}", response.getUserProfile().getEmail());
        return ResponseEntity.ok(response);
    }
}
