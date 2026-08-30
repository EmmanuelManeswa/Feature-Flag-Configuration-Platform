package com.featureflagplatform.ai.provider;

/** Every failure mode the assessment explicitly calls out, so callers can log/react per-reason. */
public enum AiFailureReason {
    TIMEOUT,
    CONNECTION_REFUSED,
    RATE_LIMITED,
    EMPTY_RESPONSE,
    PROVIDER_ERROR
}
