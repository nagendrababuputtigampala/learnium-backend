package com.learnium.api;

import com.learnium.api.dto.AuthDtos.AcceptInviteRequest;
import com.learnium.api.dto.AuthDtos.AuthResponse;
import com.learnium.api.dto.AuthDtos.ForgotPasswordRequest;
import com.learnium.api.dto.AuthDtos.LoginRequest;
import com.learnium.api.dto.AuthDtos.LogoutRequest;
import com.learnium.api.dto.AuthDtos.RefreshRequest;
import com.learnium.api.dto.AuthDtos.ResetPasswordRequest;
import com.learnium.auth.AccountFlowService;
import com.learnium.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff authentication door: login, refresh rotation, logout, emailed-link flows. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountFlowService accountFlowService;

    public AuthController(AuthService authService, AccountFlowService accountFlowService) {
        this.authService = authService;
        this.accountFlowService = accountFlowService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** Always 202 — the response never reveals whether the email has an account. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accountFlowService.forgotPassword(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountFlowService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accept-invite")
    public ResponseEntity<Void> acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        accountFlowService.acceptInvite(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
