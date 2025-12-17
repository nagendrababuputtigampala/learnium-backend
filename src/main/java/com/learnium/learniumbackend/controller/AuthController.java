package com.learnium.learniumbackend.controller;

import com.learnium.learniumbackend.entity.request.AuthRequest;
import com.learnium.learniumbackend.entity.response.AuthResponse;
import com.learnium.learniumbackend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "APIs for user authentication")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "User Sign-In")
    @Parameters({
            @Parameter(name = "authRequest", description = "Authentication request payload")
    })
    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signIn(
            @RequestBody(required = false) AuthRequest authRequest) {
        AuthResponse response = authService.signIn(authRequest);
        logger.info("Login API success for user: {}", response.getUserProfile().getEmail());
        return ResponseEntity.ok(response);
    }
}
