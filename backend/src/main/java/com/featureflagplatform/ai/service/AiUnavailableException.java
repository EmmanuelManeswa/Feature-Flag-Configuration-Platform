package com.featureflagplatform.ai.service;

/**
 * The one exception the client ever sees for any AI failure — provider
 * unreachable, timeout, rate limited, malformed JSON, or a response that
 * failed schema/domain validation. The specific reason is always logged
 * server-side with the correlation ID; the client gets a single friendly
 * message ("AI unavailable — configure manually") regardless of which
 * internal failure mode occurred, per the assessment's own example failure
 * copy. Mapped to 503 by GlobalExceptionHandler.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String internalReason) {
        super(internalReason);
    }

    public AiUnavailableException(String internalReason, Throwable cause) {
        super(internalReason, cause);
    }
}
