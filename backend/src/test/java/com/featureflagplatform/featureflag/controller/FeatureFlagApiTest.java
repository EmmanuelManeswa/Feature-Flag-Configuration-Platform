package com.featureflagplatform.featureflag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflagplatform.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack API tests: real HTTP requests through MockMvc against a real
 * Spring context (Testcontainers Postgres/Redis), not service-layer calls.
 * Where {@link com.featureflagplatform.featureflag.service.FeatureFlagServiceIntegrationTest}
 * proves the service logic is correct, this proves the same about the HTTP
 * layer sitting in front of it — auth, authorization, validation shape, and
 * status codes as an actual client would see them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FeatureFlagApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    @Test
    void listFlagsWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/flags"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void loginWithWrongPasswordIs401NotAStackTrace() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"admin@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:problem-type:authentication-error"))
                .andExpect(jsonPath("$.correlationId").exists())
                // The response must never contain a stack trace or exception class name.
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    org.assertj.core.api.Assertions.assertThat(body).doesNotContain("Exception", "\tat ");
                });
    }

    @Test
    void viewerCanListButCannotCreateFlags() throws Exception {
        String viewerToken = login("viewer@example.com", "Password123!");

        mockMvc.perform(get("/api/v1/flags")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/flags")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType("application/json")
                        .content("{\"key\":\"viewer-attempt\",\"name\":\"x\",\"environmentId\":\"00000000-0000-0000-0000-000000000000\",\"type\":\"BOOLEAN\",\"enabled\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:problem-type:access-denied"));
    }

    @Test
    void createWithMissingFieldsReturnsFieldLevelValidationErrors() throws Exception {
        String adminToken = login("admin@example.com", "Password123!");

        mockMvc.perform(post("/api/v1/flags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"))
                .andExpect(jsonPath("$.errors.key").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.environmentId").exists())
                .andExpect(jsonPath("$.errors.type").exists());
    }

    @Test
    void unknownFlagIdReturns404WithProblemDetail() throws Exception {
        String adminToken = login("admin@example.com", "Password123!");

        mockMvc.perform(get("/api/v1/flags/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:problem-type:not-found"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void listIsPaginatedAndEveryResponseCarriesACorrelationIdHeader() throws Exception {
        String adminToken = login("admin@example.com", "Password123!");

        mockMvc.perform(get("/api/v1/flags?size=2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void suppliedCorrelationIdIsEchoedBackUnchanged() throws Exception {
        mockMvc.perform(get("/api/v1/flags").header("X-Correlation-ID", "my-own-trace-id-123"))
                .andExpect(header().string("X-Correlation-ID", "my-own-trace-id-123"));
    }

    @Test
    void metricsEndpointCountsEvaluationsByResult() throws Exception {
        String adminToken = login("admin@example.com", "Password123!");
        String flagId = createBooleanFlag(adminToken, "metrics-test-" + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/flags/" + flagId + "/metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvaluations").value(0))
                .andExpect(jsonPath("$.countsByResult").isEmpty());

        mockMvc.perform(post("/api/v1/flags/" + flagId + "/evaluate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"stableIdentifier\":\"user-1\",\"attributes\":{}}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/flags/" + flagId + "/evaluate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"stableIdentifier\":\"user-2\",\"attributes\":{}}"))
                .andExpect(status().isOk());

        // Flag is enabled=true, BOOLEAN type: every evaluation resolves "true"
        // regardless of stableIdentifier (see FeatureFlagEvaluator).
        mockMvc.perform(get("/api/v1/flags/" + flagId + "/metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvaluations").value(2))
                .andExpect(jsonPath("$.countsByResult.true").value(2));
    }

    @Test
    void streamEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/flags/stream"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void streamEndpointOpensAnEventStreamAndSendsAConnectedEventImmediately() throws Exception {
        String adminToken = login("admin@example.com", "Password123!");

        // The controller method sends the "connected" event synchronously
        // before returning the SseEmitter, so it's already in the response
        // buffer by the time the async dispatch starts — no need to wait for
        // (or trigger) the request to actually complete, which for this
        // endpoint, by design, it never does within a normal browsing
        // session. This connection is intentionally left open for the rest
        // of the test JVM's life, mirroring a real long-lived SSE subscriber.
        MvcResult result = mockMvc.perform(get("/api/v1/flags/stream")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Content-Type", "text/event-stream"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:connected").contains("\"status\":\"connected\"");
    }

    private String createBooleanFlag(String adminToken, String key) throws Exception {
        String devEnvironmentId = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/environments").header("Authorization", "Bearer " + adminToken))
                                .andReturn().getResponse().getContentAsString())
                .get(0).get("id").asText();

        String body = mockMvc.perform(post("/api/v1/flags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"key\":\"%s\",\"name\":\"Metrics Test Flag\",\"environmentId\":\"%s\",\"type\":\"BOOLEAN\",\"enabled\":true}"
                                .formatted(key, devEnvironmentId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }
}
