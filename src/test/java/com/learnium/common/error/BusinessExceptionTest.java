package com.learnium.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    void defaultMessageComesFromErrorCode() {
        BusinessException ex = new BusinessException(ErrorCode.AUTH_FORBIDDEN);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        assertThat(ex.getMessage()).isEqualTo("Access denied");
    }

    @Test
    void customMessageOverridesDefault() {
        BusinessException ex = new BusinessException(ErrorCode.VAL_FAILED, "Name is required");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VAL_FAILED);
        assertThat(ex.getMessage()).isEqualTo("Name is required");
    }
}
