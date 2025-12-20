package com.learnium.learniumbackend.controller;

import com.learnium.learniumbackend.entity.request.auth.AuthBootstrapRequest;
import com.learnium.learniumbackend.entity.response.auth.AuthBootstrapResponse;
import com.learnium.learniumbackend.exception.InvalidAuthTokenException;
import com.learnium.learniumbackend.mapper.auth.AuthBootstrapMapper;
import com.learnium.learniumbackend.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthBootstrapMapper mapper;

    @PostMapping("/bootstrap")
    public ResponseEntity<AuthBootstrapResponse> bootstrap(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody(required = false) AuthBootstrapRequest request) {
        String token = extractBearerToken(authorization);
        var authBootstrapResponse = authService.bootstrap(token, request);
        return ResponseEntity.ok(authBootstrapResponse);
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.isNotBlank(authorization) || !authorization.startsWith("Bearer ")) {
            throw new InvalidAuthTokenException("Missing Authorization Bearer token", null);
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
