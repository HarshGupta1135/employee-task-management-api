package com.harsh.taskmanager.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.harsh.taskmanager.exception.ApiError.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Translates exceptions thrown by controllers and services into {@link ApiError} responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    public static final String FORBIDDEN_MESSAGE = "You do not have permission to perform this action";
    private static final String VALIDATION_FAILED = "Validation failed";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** Safety net for sort properties that reach Spring Data without passing through {@code SortValidator}. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handleUnknownProperty(PropertyReferenceException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Unknown property '" + ex.getPropertyName() + "'", request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "The resource was modified by another request. Reload it and try again.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data", request);
    }

    /** {@code @Valid @RequestBody} failures. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> violations.add(toViolation(error)));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> violations.add(new FieldViolation(error.getObjectName(), error.getDefaultMessage())));
        return validation(violations, request);
    }

    /** Constraint annotations placed directly on controller method parameters (for example {@code @Size} on a query parameter). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                errors.getFieldErrors().forEach(error -> violations.add(toViolation(error)));
            } else {
                String parameter = result.getMethodParameter().getParameterName();
                result.getResolvableErrors()
                        .forEach(error -> violations.add(new FieldViolation(parameter, error.getDefaultMessage())));
            }
        }
        return validation(violations, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(lastNode(violation.getPropertyPath().toString()), violation.getMessage()))
                .toList();
        return validation(violations, request);
    }

    /** Malformed JSON, or a value of the wrong type (for example an unknown enum constant) in the request body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            String message = describeInvalidValue(mismatch.getTargetType());
            return validation(List.of(new FieldViolation(field, message)), request);
        }
        return build(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON", request);
    }

    /** A query or path parameter that cannot be converted, e.g. {@code /api/tasks/abc} or {@code ?status=UNKNOWN}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value '" + ex.getValue() + "'. " + describeInvalidValue(ex.getRequiredType());
        return validation(List.of(new FieldViolation(ex.getName(), message)), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        String message = ex instanceof BadCredentialsException ? "Invalid email or password" : ex.getMessage();
        return build(HttpStatus.UNAUTHORIZED, message, request);
    }

    /** Raised by {@code @PreAuthorize} checks and by ownership checks in the service layer. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String message = ex instanceof AuthorizationDeniedException ? FORBIDDEN_MESSAGE : ex.getMessage();
        return build(HttpStatus.FORBIDDEN, message, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint " + request.getMethod() + " " + request.getRequestURI(), request);
    }

    /**
     * Fallback. Spring MVC exceptions that already carry an HTTP status (405, 415, 404 for unknown paths,
     * missing parameters, ...) keep that status; anything else is an unexpected 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            String detail = errorResponse.getBody().getDetail();
            return build(status, detail != null ? detail : status.getReasonPhrase(), request);
        }
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }

    private static String describeInvalidValue(Class<?> targetType) {
        if (targetType != null && targetType.isEnum()) {
            return "Allowed values: " + Arrays.toString(targetType.getEnumConstants());
        }
        if (LocalDate.class.equals(targetType)) {
            return "Expected a date in yyyy-MM-dd format";
        }
        if (targetType != null && Number.class.isAssignableFrom(targetType)) {
            return "Expected a numeric value";
        }
        if (targetType != null) {
            return "Expected a value of type " + targetType.getSimpleName();
        }
        return "Value has an invalid format";
    }

    private static String lastNode(String propertyPath) {
        int index = propertyPath.lastIndexOf('.');
        return index >= 0 ? propertyPath.substring(index + 1) : propertyPath;
    }

    private static ResponseEntity<ApiError> validation(List<FieldViolation> violations, HttpServletRequest request) {
        // Bean Validation reports violations in no particular order; sort them so responses are deterministic.
        List<FieldViolation> sorted = violations.stream()
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message))
                .toList();
        return ResponseEntity.badRequest().body(ApiError.validation(VALIDATION_FAILED, request.getRequestURI(), sorted));
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.of(status, message, request.getRequestURI()));
    }
}
