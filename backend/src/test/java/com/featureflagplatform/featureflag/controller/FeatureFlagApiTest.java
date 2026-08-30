package com.featureflagplatform.featureflag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflagplatform.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
}
