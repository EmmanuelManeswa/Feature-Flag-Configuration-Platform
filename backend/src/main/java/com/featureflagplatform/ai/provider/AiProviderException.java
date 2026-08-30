package com.featureflagplatform.ai.provider;

/**
 * Thrown by an {@link AiProvider} when it could not obtain a completion at
 * all — as opposed to obtaining one that turns out to be malformed or
 * invalid, which is a concern for the caller (see
 * {@code AiRuleAssistantService}), not the provider.
 */
public class AiProviderException extends RuntimeException {

    private final AiFailureReason reason;

    public AiProviderException(AiFailureReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public AiProviderException(AiFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AiFailureReason reason() {
        return reason;
    }
}
