package com.learnium.api;

import com.learnium.api.dto.AuthDtos.MeResponse;
import com.learnium.auth.AuthService;
import com.learnium.security.CurrentPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The current caller's identity, for any authenticated principal (staff or student). */
@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me() {
        return ResponseEntity.ok(authService.me(CurrentPrincipal.require()));
    }
}
