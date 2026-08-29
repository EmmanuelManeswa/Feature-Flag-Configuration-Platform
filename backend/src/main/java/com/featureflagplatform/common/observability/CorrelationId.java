package com.featureflagplatform.common.observability;

import org.slf4j.MDC;

/** Small accessor so callers don't need to know the MDC key name directly. */
public final class CorrelationId {

    private CorrelationId() {
    }

    public static String current() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value == null ? "unknown" : value;
    }
}
