package com.featureflagplatform.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Every request gets a correlation ID: propagated from an incoming
 * {@code X-Correlation-ID} header if the caller supplied one (useful when a
 * client wants to tie its own logs to ours), otherwise generated fresh. It's
 * echoed back on the response, placed in the logging MDC for the lifetime of
 * the request (so every log line emitted while handling it carries the same
 * ID — see {@code logback-spring.xml}), and available to
 * {@link com.featureflagplatform.common.exception.GlobalExceptionHandler} and
 * the audit service so error responses and audit rows can all be tied back to
 * the request that caused them.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
