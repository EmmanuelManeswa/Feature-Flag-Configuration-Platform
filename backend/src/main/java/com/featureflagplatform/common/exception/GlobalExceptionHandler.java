package com.featureflagplatform.common.exception;

import com.featureflagplatform.ai.service.AiUnavailableException;
import com.featureflagplatform.common.observability.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error response leaving this API — validation failures, not-found,
 * stale-version conflicts, auth failures, and anything unexpected — comes out
 * shaped as an RFC 7807 {@link ProblemDetail}, always carrying a
 * {@code correlationId} that also appears in the server logs for that
 * request. Nothing here ever puts a stack trace, SQL error text, or internal
 * exception message into a client-facing response; unexpected exceptions are
 * logged in full server-side and reduced to a generic message for the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final URI TYPE_VALIDATION = URI.create("urn:problem-type:validation-error");
    private static final URI TYPE_NOT_FOUND = URI.create("urn:problem-type:not-found");
    private static final URI TYPE_CONFLICT = URI.create("urn:problem-type:conflict");
    private static final URI TYPE_STALE_VERSION = URI.create("urn:problem-type:stale-version");
    private static final URI TYPE_AUTH = URI.create("urn:problem-type:authentication-error");
    private static final URI TYPE_ACCESS_DENIED = URI.create("urn:problem-type:access-denied");
    private static final URI TYPE_INTERNAL = URI.create("urn:problem-type:internal-error");
    private static final URI TYPE_AI_UNAVAILABLE = URI.create("urn:problem-type:ai-unavailable");

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request contains invalid fields.");
        problem.setTitle("Validation failed");
        problem.setType(TYPE_VALIDATION);

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(
                fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        problem.setProperty("errors", fieldErrors);
        enrich(problem, request);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        problem.setType(TYPE_NOT_FOUND);
        enrich(problem, request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(StaleVersionConflictException.class)
    public ResponseEntity<ProblemDetail> handleStaleVersion(StaleVersionConflictException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Stale version");
        problem.setType(TYPE_STALE_VERSION);
        problem.setProperty("currentVersion", ex.currentVersion());
        problem.setProperty("expectedVersion", ex.expectedVersion());
        enrich(problem, request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        // Belt-and-braces: reachable if two requests both pass the service-layer
        // version check and then race at commit time. See ADR on optimistic concurrency.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "This configuration was modified by another user. Refresh and try again.");
        problem.setTitle("Stale version");
        problem.setType(TYPE_STALE_VERSION);
        enrich(problem, request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation [correlationId={}]", CorrelationId.current(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The request conflicts with an existing resource (e.g. a duplicate key).");
        problem.setTitle("Conflict");
        problem.setType(TYPE_CONFLICT);
        enrich(problem, request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler({ org.springframework.security.core.AuthenticationException.class, BadCredentialsException.class })
    public ResponseEntity<ProblemDetail> handleAuthentication(Exception ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication failed.");
        problem.setTitle("Unauthorized");
        problem.setType(TYPE_AUTH);
        enrich(problem, request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
        problem.setTitle("Forbidden");
        problem.setType(TYPE_ACCESS_DENIED);
        enrich(problem, request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleAiUnavailable(AiUnavailableException ex, HttpServletRequest request) {
        // The specific reason (provider timeout, malformed JSON, failed schema
        // validation, ...) is in the exception message/cause and already logged
        // by the caller with the correlation ID — the client gets only the
        // generic, assessment-specified copy, never which internal failure mode
        // occurred.
        log.warn("AI unavailable [correlationId={}]: {}", CorrelationId.current(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Unable to generate a rule proposal right now. You can configure the rule manually.");
        problem.setTitle("AI unavailable");
        problem.setType(TYPE_AI_UNAVAILABLE);
        enrich(problem, request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid request");
        problem.setType(TYPE_VALIDATION);
        enrich(problem, request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Full detail goes server-side only. The client gets nothing that could
        // leak internals (stack trace, SQL, class names) — just a correlation ID
        // to quote when reporting the issue.
        log.error("Unhandled exception [correlationId={}]", CorrelationId.current(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again or contact support with the correlation ID.");
        problem.setTitle("Internal server error");
        problem.setType(TYPE_INTERNAL);
        enrich(problem, request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private void enrich(ProblemDetail problem, WebRequest request) {
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problem.setProperty("correlationId", CorrelationId.current());
    }

    private void enrich(ProblemDetail problem, HttpServletRequest request) {
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", CorrelationId.current());
    }
}
