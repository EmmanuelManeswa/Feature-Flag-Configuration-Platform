package com.featureflagplatform.ai.provider;

/**
 * The one seam between this application and any LLM. Deliberately narrow —
 * "given a system prompt and a user prompt, return raw completion text" —
 * and knows nothing about feature flags, {@code RuleProposal} shapes, or
 * targeting rules; all of that lives one layer up in
 * {@code ai.service.AiRuleAssistantService}, which is what makes this
 * interface swappable (mock today, Docker Model Runner today, OpenAI/
 * Anthropic tomorrow) without touching any domain logic.
 */
public interface AiProvider {

    /**
     * @throws AiProviderException if a completion could not be obtained at
     *                              all (timeout, connection refused, rate
     *                              limited, empty response, provider error).
     *                              A completion that comes back but turns
     *                              out to be malformed JSON or fails schema
     *                              validation is returned normally here —
     *                              that's the caller's problem to detect.
     */
    String complete(String systemPrompt, String userPrompt);
}
