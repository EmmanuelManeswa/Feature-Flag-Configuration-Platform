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
import java.util.regex.Pattern;

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
 *
 * <p>The incoming header value is untrusted client input: it's validated
 * against a strict allowlist pattern before being reflected onto the
 * response, written to logs, or persisted into an audit row. An
 * unrecognized/malformed value is discarded in favor of a freshly generated
 * ID rather than passed through — this closes both response-splitting
 * (arbitrary bytes echoed into a response header) and log-injection (fake
 * log lines forged via CR/LF in a value that ends up in every log line for
 * the request) in one place, at the one point where external input enters
 * this value.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    // Conservative allowlist: matches a UUID and any similarly-shaped
    // opaque client-generated ID (letters, digits, hyphens, underscores),
    // capped at a sane length. Anything else is treated as absent.
    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String suppliedCorrelationId = request.getHeader(HEADER_NAME);
        String correlationId = isValid(suppliedCorrelationId) ? suppliedCorrelationId : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static boolean isValid(String value) {
        return value != null && VALID_CORRELATION_ID.matcher(value).matches();
    }
}
