package com.featureflagplatform.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a "bearerAuth" security scheme and applies it globally so
 * Swagger UI shows an "Authorize" button: paste an access token obtained
 * from {@code POST /api/v1/auth/login} once, and every protected endpoint
 * in the UI can be exercised directly from there without re-entering it.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Feature Flag & Configuration Platform API")
                        .description("""
                                Manage feature flags and environments, evaluate flags deterministically \
                                for a given user context, review an immutable audit trail, and generate \
                                AI-assisted (human-reviewed) targeting rule proposals.

                                **Authentication**: call `POST /api/v1/auth/login` with one of the demo \
                                accounts (see the project README), then click **Authorize** below and paste \
                                the returned `accessToken` (no need to type "Bearer " — it's added \
                                automatically). Demo accounts: `admin@example.com` (ADMIN) and \
                                `viewer@example.com` (VIEWER), both with password `Password123!`.

                                **Roles**: ADMIN can create/update/delete flags and environments and use the \
                                AI rule assistant; VIEWER can read everything and evaluate flags. Every \
                                mutating endpoint enforces this server-side regardless of what the UI shows.""")
                        .version("v1")
                        .contact(new Contact().name("Feature Flag & Configuration Platform")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .name(BEARER_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
