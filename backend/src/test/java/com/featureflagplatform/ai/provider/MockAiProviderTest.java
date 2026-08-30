package com.featureflagplatform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiProviderTest {

    private final MockAiProvider provider = new MockAiProvider();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void handlesTheAssessmentsOwnCanonicalExample() throws Exception {
        String json = provider.complete("system", "enable this for 20% of users in Harare except internal staff");
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("strategy").asText()).isEqualTo("PERCENTAGE_ROLLOUT");
        assertThat(node.get("rolloutPercentage").asInt()).isEqualTo(20);

        var attributes = node.get("rules").findValuesAsText("attribute");
        assertThat(attributes).contains("location", "userType");

        var values = node.get("rules").findValuesAsText("value");
        assertThat(values).contains("Harare", "INTERNAL_STAFF");
    }

    @Test
    void withNoPercentageMentionedFallsBackToBoolean() throws Exception {
        String json = provider.complete("system", "turn this on for everyone");
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("strategy").asText()).isEqualTo("BOOLEAN");
        assertThat(node.get("rolloutPercentage").isNull()).isTrue();
    }

    @Test
    void clampsAnOutOfRangePercentageToOneHundred() throws Exception {
        String json = provider.complete("system", "enable this for 150% of users");
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("rolloutPercentage").asInt()).isEqualTo(100);
    }

    @Test
    void alwaysProducesValidJson() throws Exception {
        // A handful of varied, slightly awkward inputs — none of them should
        // ever produce something that fails to parse, since this provider
        // has no external dependency to blame a failure on.
        for (String input : new String[] {
                "enable it",
                "50% rollout for the Engineering department",
                "just turn it on already!!! for 5% in Cairo",
                ""
        }) {
            String json = provider.complete("system", input);
            assertThat(objectMapper.readTree(json)).isNotNull();
        }
    }
}
