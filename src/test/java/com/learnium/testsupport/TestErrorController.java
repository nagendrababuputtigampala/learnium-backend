package com.learnium.testsupport;

import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints used to exercise {@code GlobalExceptionHandler} through MockMvc.
 * Lives in test sources only; never shipped in the application jar.
 */
@RestController
@RequestMapping("/test-support")
public class TestErrorController {

    public record ValidatedPayload(@NotBlank(message = "must not be blank") String name) {}

    @PostMapping("/validated")
    public ResponseEntity<String> validated(@Valid @RequestBody ValidatedPayload payload) {
        return ResponseEntity.ok(payload.name());
    }

    @GetMapping("/business")
    public ResponseEntity<String> business() {
        throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Custom forbidden message");
    }

    /** Role-guarded endpoint: only CENTRE_ADMIN may pass; everyone else gets a 403. */
    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('CENTRE_ADMIN')")
    public ResponseEntity<String> adminOnly() {
        return ResponseEntity.ok("admin");
    }

    @GetMapping("/boom")
    public ResponseEntity<String> boom() {
        throw new IllegalStateException("sensitive internal detail");
    }
}
