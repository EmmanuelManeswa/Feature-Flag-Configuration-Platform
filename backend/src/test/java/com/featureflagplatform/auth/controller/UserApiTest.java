package com.featureflagplatform.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflagplatform.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack tests for user management and self-service password change,
 * through real HTTP requests — same pattern as {@code FeatureFlagApiTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserApiTest {

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
    void listUsersWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCannotListOrCreateUsers() throws Exception {
        String viewerToken = login("viewer@example.com", "Password123!");

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType("application/json")
                        .content("{\"email\":\"nope@example.com\",\"displayName\":\"Nope\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesAUserAndTheGeneratedPasswordActuallyLogsIn() throws Exception {
        String adminToken = login("admin@example.com", "Password123!");
        String email = "new-user-" + UUID.randomUUID() + "@example.com";

        String createBody = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"displayName\":\"New Person\",\"role\":\"VIEWER\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.enabled").value(true))
                .andExpect(jsonPath("$.generatedPassword").exists())
                .andReturn().getResponse().getContentAsString();

        String generatedPassword = objectMapper.readTree(createBody).get("generatedPassword").asText();

        // The real proof the whole pipeline works: the password this test
        // never chose, generated purely server-side, actually authenticates.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, generatedPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void creatingAUserWithATakenEmailIsRejected() throws Exception {
        String adminToken = login("admin@example.com", "Password123!");

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"email\":\"admin@example.com\",\"displayName\":\"Duplicate\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"));
    }

    @Test
    void anAdminCannotDisableTheirOwnAccountViaTheApi() throws Exception {
        String adminToken = login("admin@example.com", "Password123!");
        String meBody = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        String ownId = objectMapper.readTree(meBody).get("id").asText();

        mockMvc.perform(post("/api/v1/users/" + ownId + "/disable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("cannot disable your own account")));
    }

    @Test
    void aDisabledUserCanNoLongerLogInOrUseAnExistingToken() throws Exception {
        String adminToken = login("admin@example.com", "Password123!");
        String email = "disable-test-" + UUID.randomUUID() + "@example.com";

        String createBody = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"displayName\":\"Disable Test\",\"role\":\"VIEWER\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        var created = objectMapper.readTree(createBody);
        String userId = created.get("user").get("id").asText();
        String generatedPassword = created.get("generatedPassword").asText();

        String newUserToken = login(email, generatedPassword);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + newUserToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/" + userId + "/disable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // The token issued before disabling was never revoked (stateless
        // JWT), but the account is now re-checked fresh from the database on
        // every request — see JwtAuthenticationFilter — so it must stop
        // working immediately, not just at the next login attempt.
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + newUserToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, generatedPassword)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRequiresTheCorrectCurrentPasswordAndTheNewOneThenWorks() throws Exception {
        String adminToken = login("admin@example.com", "Password123!");
        String email = "changepw-test-" + UUID.randomUUID() + "@example.com";

        String createBody = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"displayName\":\"Change PW Test\",\"role\":\"VIEWER\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        String generatedPassword = objectMapper.readTree(createBody).get("generatedPassword").asText();
        String userToken = login(email, generatedPassword);

        mockMvc.perform(put("/api/v1/auth/me/password")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"BrandNewPass1!\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/auth/me/password")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"BrandNewPass1!\"}".formatted(generatedPassword)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"BrandNewPass1!\"}".formatted(email)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, generatedPassword)))
                .andExpect(status().isUnauthorized());
    }
}
