package com.featureflagplatform.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflagplatform.common.observability.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Invoked by Spring Security's {@code ExceptionTranslationFilter} when a
 * request reaches an {@code authorizeHttpRequests().authenticated()} rule
 * with no authentication at all (missing/invalid bearer token) — a rejection
 * that happens at the filter level, before Spring MVC dispatch, so it never
 * reaches {@link com.featureflagplatform.common.exception.GlobalExceptionHandler}.
 * Without this, Spring Security's default behavior for a pure-API (no
 * form-login) configuration is a bare, empty-bodied 403 — wrong status
 * (401 is "who are you", 403 is "I know who you are and you can't do this")
 * and inconsistent with every other error response's RFC 7807 shape. This
 * produces the same {@link ProblemDetail} shape as everything else, with the
 * correlation ID that {@code CorrelationIdFilter} set before Security's
 * chain ran.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource.");
        problem.setTitle("Unauthorized");
        problem.setType(URI.create("urn:problem-type:authentication-error"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", CorrelationId.current());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
