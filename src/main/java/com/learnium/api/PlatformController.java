package com.learnium.api;

import com.learnium.admin.PlatformService;
import com.learnium.api.dto.AdminDtos.CreateCentreRequest;
import com.learnium.api.dto.AdminDtos.CreateCentreResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform onboarding. The provisioning key header is the credential (the
 * route is permit-listed; the key check runs in {@link PlatformService}),
 * and the endpoint answers 404 when no key is configured at all.
 */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {

    private final PlatformService platformService;

    public PlatformController(PlatformService platformService) {
        this.platformService = platformService;
    }

    @PostMapping("/centres")
    public ResponseEntity<CreateCentreResponse> createCentre(
            @RequestHeader(value = "X-Provisioning-Key", required = false) String provisioningKey,
            @Valid @RequestBody CreateCentreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformService.createCentre(provisioningKey, request));
    }
}
