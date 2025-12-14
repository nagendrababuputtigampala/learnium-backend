package com.learnium.learniumbackend.controller;

import com.learnium.learniumbackend.dto.ProfileData;
import com.learnium.learniumbackend.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);
    private final ProfileService profileService;

    @Autowired
    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<ProfileData> getMyProfile(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        logger.info("Profile API called");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            logger.warn("Missing or invalid Authorization header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String idToken = authorizationHeader.substring(7);
        try {
            ProfileData profile = profileService.getProfileByIdToken(idToken);
            logger.info("Profile fetched for user");
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            logger.error("Failed to fetch profile: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
