package com.learnium.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    @Test
    void sysUnexpectedHasRegisteredValues() {
        assertThat(ErrorCode.SYS_UNEXPECTED.getCode()).isEqualTo("LRN-SYS-001");
        assertThat(ErrorCode.SYS_UNEXPECTED.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ErrorCode.SYS_UNEXPECTED.getDefaultMessage()).isEqualTo("Unexpected server error");
    }

    @Test
    void sysNotFoundHasRegisteredValues() {
        assertThat(ErrorCode.SYS_NOT_FOUND.getCode()).isEqualTo("LRN-SYS-404");
        assertThat(ErrorCode.SYS_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.SYS_NOT_FOUND.getDefaultMessage()).isEqualTo("Resource not found");
    }

    @Test
    void valFailedHasRegisteredValues() {
        assertThat(ErrorCode.VAL_FAILED.getCode()).isEqualTo("LRN-VAL-001");
        assertThat(ErrorCode.VAL_FAILED.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.VAL_FAILED.getDefaultMessage()).isEqualTo("Validation failed");
    }

    @Test
    void valMalformedBodyHasRegisteredValues() {
        assertThat(ErrorCode.VAL_MALFORMED_BODY.getCode()).isEqualTo("LRN-VAL-002");
        assertThat(ErrorCode.VAL_MALFORMED_BODY.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.VAL_MALFORMED_BODY.getDefaultMessage()).isEqualTo("Malformed request body");
    }

    @Test
    void sysMethodNotAllowedHasRegisteredValues() {
        assertThat(ErrorCode.SYS_METHOD_NOT_ALLOWED.getCode()).isEqualTo("LRN-SYS-405");
        assertThat(ErrorCode.SYS_METHOD_NOT_ALLOWED.getStatus())
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(ErrorCode.SYS_METHOD_NOT_ALLOWED.getDefaultMessage()).isEqualTo("Method not allowed");
    }

    @Test
    void sysUnsupportedMediaTypeHasRegisteredValues() {
        assertThat(ErrorCode.SYS_UNSUPPORTED_MEDIA_TYPE.getCode()).isEqualTo("LRN-SYS-415");
        assertThat(ErrorCode.SYS_UNSUPPORTED_MEDIA_TYPE.getStatus())
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(ErrorCode.SYS_UNSUPPORTED_MEDIA_TYPE.getDefaultMessage())
                .isEqualTo("Unsupported media type");
    }

    @Test
    void authUnauthenticatedHasRegisteredValues() {
        assertThat(ErrorCode.AUTH_UNAUTHENTICATED.getCode()).isEqualTo("LRN-AUTH-001");
        assertThat(ErrorCode.AUTH_UNAUTHENTICATED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.AUTH_UNAUTHENTICATED.getDefaultMessage()).isEqualTo("Authentication required");
    }

    @Test
    void authForbiddenHasRegisteredValues() {
        assertThat(ErrorCode.AUTH_FORBIDDEN.getCode()).isEqualTo("LRN-AUTH-003");
        assertThat(ErrorCode.AUTH_FORBIDDEN.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.AUTH_FORBIDDEN.getDefaultMessage()).isEqualTo("Access denied");
    }

    @Test
    void everyConstantIsFullyDefined() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(errorCode.getCode()).isNotBlank();
            assertThat(errorCode.getStatus()).isNotNull();
            assertThat(errorCode.getDefaultMessage()).isNotBlank();
        }
    }
}
