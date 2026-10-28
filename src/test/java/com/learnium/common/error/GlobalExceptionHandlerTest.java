package com.learnium.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void businessExceptionWith4xxCodeReturnsItsStatusAndMessage() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleBusinessException(new BusinessException(ErrorCode.AUTH_FORBIDDEN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("LRN-AUTH-003");
        assertThat(body.getMessage()).isEqualTo("Access denied");
        assertThat(body.getFieldErrors()).isNull();
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    void businessExceptionWith5xxCodeReturns500() {
        ResponseEntity<ApiErrorResponse> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.SYS_UNEXPECTED, "kaput"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("LRN-SYS-001");
        assertThat(body.getMessage()).isEqualTo("kaput");
    }

    @Test
    void traceIdComesFromMdcWhenPresent() {
        MDC.put("traceId", "trace-from-mdc");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleBusinessException(new BusinessException(ErrorCode.AUTH_UNAUTHENTICATED));

        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTraceId()).isEqualTo("trace-from-mdc");
    }

    @Test
    void traceIdFallsBackToUnknownWhenMdcEmpty() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleBusinessException(new BusinessException(ErrorCode.AUTH_UNAUTHENTICATED));

        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTraceId()).isEqualTo("unknown");
    }

    @Test
    void validationExceptionCollectsFieldErrorsKeepingFirstMessagePerField() throws Exception {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Payload(), "payload");
        bindingResult.rejectValue("name", "NotBlank", "must not be blank");
        bindingResult.rejectValue("name", "Size", "too short");
        bindingResult.rejectValue("grade", "NotNull", "must not be null");
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("LRN-VAL-001");
        assertThat(body.getMessage()).isEqualTo("Validation failed");
        assertThat(body.getFieldErrors())
                .containsEntry("name", "must not be blank")
                .containsEntry("grade", "must not be null")
                .hasSize(2);
    }

    @Test
    void validationExceptionWithNoFieldErrorsReturnsEmptyMap() throws Exception {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "payload");
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidationException(ex);

        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getFieldErrors()).isEmpty();
    }

    @Test
    void notFoundExceptionReturnsSys404() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleNotFoundException(
                        new NoResourceFoundException(HttpMethod.GET, "/api/v1/nope", "static"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("LRN-SYS-404");
        assertThat(body.getMessage()).isEqualTo("Resource not found");
    }

    @Test
    void unexpectedExceptionReturnsGeneric500WithoutInternalDetail() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedException(
                new IllegalStateException("db password hunter2 refused"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("LRN-SYS-001");
        assertThat(body.getMessage()).isEqualTo("Unexpected server error");
        assertThat(body.getMessage()).doesNotContain("hunter2");
    }

    /** Minimal bean so BeanPropertyBindingResult can read the rejected properties. */
    private static class Payload {

        public String getName() {
            return null;
        }

        public Integer getGrade() {
            return null;
        }
    }

    private MethodParameter dummyMethodParameter() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("dummyEndpoint", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void dummyEndpoint(String payload) {
        // Only used to obtain a MethodParameter for exception construction.
    }
}
