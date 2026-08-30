package com.featureflagplatform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflagplatform.ai.dto.RuleAssistantRequest;
import com.featureflagplatform.ai.provider.AiFailureReason;
import com.featureflagplatform.ai.provider.AiProvider;
import com.featureflagplatform.ai.provider.AiProviderException;
import com.featureflagplatform.evaluation.domain.FlagType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Mocks {@link AiProvider} throughout — per the assessment's AI testing
 * requirement, this proves the assistant handles a valid response, malformed
 * JSON, a structurally-valid-but-domain-invalid response, and every provider
 * failure mode (timeout, connection refused, rate limited, empty response,
 * generic provider error) without ever letting untrusted AI output reach a
 * caller unvalidated.
 */
@ExtendWith(MockitoExtension.class)
class AiRuleAssistantServiceTest {

    @Mock
    private AiProvider aiProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private AiRuleAssistantService service() {
        return new AiRuleAssistantService(aiProvider, objectMapper, validator);
    }

    @Test
    void validResponseProducesAValidatedProposal() {
        when(aiProvider.complete(any(), any())).thenReturn("""
                {"strategy":"PERCENTAGE_ROLLOUT","rolloutPercentage":20,"rules":[
                    {"attribute":"location","operator":"EQUALS","value":"Harare"},
                    {"attribute":"userType","operator":"NOT_EQUALS","value":"INTERNAL_STAFF"}
                ],"explanation":"Enable for 20% of users in Harare, excluding internal staff."}
                """);

        var proposal = service().generateProposal(new RuleAssistantRequest("enable this for 20% of users in Harare except internal staff"));

        assertThat(proposal.strategy()).isEqualTo(FlagType.PERCENTAGE_ROLLOUT);
        assertThat(proposal.rolloutPercentage()).isEqualTo(20);
        assertThat(proposal.rules()).hasSize(2);
        assertThat(proposal.rules().get(0).attribute()).isEqualTo("location");
        assertThat(proposal.explanation()).isNotBlank();
    }

    @Test
    void proseWrappedJsonIsExtractedAndParsedCorrectly() {
        when(aiProvider.complete(any(), any())).thenReturn("""
                Sure, here is your JSON:
                ```json
                {"strategy":"BOOLEAN","rolloutPercentage":null,"rules":[],"explanation":"Enable for everyone."}
                ```
                Let me know if you need anything else!
                """);

        var proposal = service().generateProposal(new RuleAssistantRequest("turn this on for everyone"));

        assertThat(proposal.strategy()).isEqualTo(FlagType.BOOLEAN);
        assertThat(proposal.rolloutPercentage()).isNull();
    }

    @Test
    void malformedJsonResultsInAiUnavailable() {
        when(aiProvider.complete(any(), any())).thenReturn("this is not json at all {{{");

        assertThatThrownBy(() -> service().generateProposal(new RuleAssistantRequest("enable this")))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void invalidOperatorFailsSchemaValidation() {
        // "CONTAINS" is not in the TargetingOperator whitelist (EQUALS/NOT_EQUALS)
        // — Jackson enum deserialization itself rejects it, which surfaces as a
        // parse failure here, exactly as it should for an unrecognized value.
        when(aiProvider.complete(any(), any())).thenReturn("""
                {"strategy":"BOOLEAN","rolloutPercentage":null,"rules":[
                    {"attribute":"location","operator":"CONTAINS","value":"Harare"}
                ],"explanation":"..."}
                """);

        assertThatThrownBy(() -> service().generateProposal(new RuleAssistantRequest("enable this")))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void rolloutPercentageOutOfRangeFailsSchemaValidation() {
        when(aiProvider.complete(any(), any())).thenReturn("""
                {"strategy":"PERCENTAGE_ROLLOUT","rolloutPercentage":150,"rules":[],"explanation":"..."}
                """);

        assertThatThrownBy(() -> service().generateProposal(new RuleAssistantRequest("enable for 150% somehow")))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void domainInvariantViolationFailsEvenWhenSchemaIsValid() {
        // Schema-valid (rolloutPercentage is a valid int, strategy is a valid
        // enum) but violates the BOOLEAN-must-have-null-rolloutPercentage rule —
        // the same domain rule FeatureFlagService enforces for human input.
        when(aiProvider.complete(any(), any())).thenReturn("""
                {"strategy":"BOOLEAN","rolloutPercentage":20,"rules":[],"explanation":"..."}
                """);

        assertThatThrownBy(() -> service().generateProposal(new RuleAssistantRequest("enable this")))
                .isInstanceOf(AiUnavailableException.class);
    }

    @ParameterizedTest
    @EnumSource(AiFailureReason.class)
    void everyProviderFailureModeResultsInAiUnavailable(AiFailureReason reason) {
        when(aiProvider.complete(any(), any())).thenThrow(new AiProviderException(reason, "simulated " + reason));

        assertThatThrownBy(() -> service().generateProposal(new RuleAssistantRequest("enable this")))
                .isInstanceOf(AiUnavailableException.class);
    }
}
