package com.learnium.api;

import com.learnium.admin.CentreService;
import com.learnium.api.dto.AdminDtos.CentreDto;
import com.learnium.api.dto.AdminDtos.UpdateCentreRequest;
import com.learnium.security.CurrentPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's centre profile: staff read it, the centre admin edits it. */
@RestController
@RequestMapping("/api/v1/centre")
public class CentreController {

    private final CentreService centreService;

    public CentreController(CentreService centreService) {
        this.centreService = centreService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
    public ResponseEntity<CentreDto> getCentre() {
        return ResponseEntity.ok(centreService.getCentre(CurrentPrincipal.require()));
    }

    @PutMapping
    @PreAuthorize("hasRole('CENTRE_ADMIN')")
    public ResponseEntity<CentreDto> updateCentre(@Valid @RequestBody UpdateCentreRequest request) {
        return ResponseEntity.ok(centreService.updateCentre(CurrentPrincipal.require(), request));
    }
}
