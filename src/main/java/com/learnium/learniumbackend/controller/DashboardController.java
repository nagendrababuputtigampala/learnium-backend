package com.learnium.learniumbackend.controller;

import com.learnium.learniumbackend.dto.DashboardResponse;
import com.learnium.learniumbackend.service.DashboardService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/users")
public class DashboardController {
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestHeader("Authorization") String authorizationHeader) {
        logger.info("Dashboard API called");
        String idToken = authorizationHeader.startsWith("Bearer ") ? authorizationHeader.substring(7) : authorizationHeader;
        // In production, validate token and fetch user-specific data
        DashboardResponse response = dashboardService.getDashboardForUser(idToken);
        logger.info("Dashboard data returned");
        return ResponseEntity.ok(response);
    }
}

