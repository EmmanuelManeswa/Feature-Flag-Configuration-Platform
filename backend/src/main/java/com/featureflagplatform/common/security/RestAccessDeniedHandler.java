package com.featureflagplatform.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflagplatform.common.observability.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * The filter-level counterpart to {@code GlobalExceptionHandler}'s
 * {@code AccessDeniedException} handler: that one catches denials raised
 * inside Spring MVC dispatch (e.g. {@code @PreAuthorize} on a service
 * method, which is how every ADMIN-only endpoint in this app actually
 * enforces its role check), but a denial raised directly by
 * {@code authorizeHttpRequests} path rules happens at the filter level and
 * needs its own handler to get the same RFC 7807 shape instead of Spring
 * Security's bare default response.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
        problem.setTitle("Forbidden");
        problem.setType(URI.create("urn:problem-type:access-denied"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", CorrelationId.current());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
