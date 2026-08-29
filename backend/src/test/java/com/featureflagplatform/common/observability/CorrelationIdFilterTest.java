package com.featureflagplatform.common.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void propagatesAWellFormedClientSuppliedCorrelationId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "client-generated-id-123");
        var response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("client-generated-id-123");
    }

    @Test
    void generatesAFreshIdWhenNoneSupplied() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
    }

    @Test
    void rejectsAndReplacesAHeaderValueContainingCrlf() throws Exception {
        // Simulates an attempted response-splitting / log-injection payload.
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "evil\r\nSet-Cookie: session=hijacked");
        var response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        String reflected = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(reflected).doesNotContain("\r", "\n", "evil", "Set-Cookie");
    }

    @Test
    void rejectsAnOverlongHeaderValue() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "a".repeat(500));
        var response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).hasSizeLessThan(200);
    }
}
