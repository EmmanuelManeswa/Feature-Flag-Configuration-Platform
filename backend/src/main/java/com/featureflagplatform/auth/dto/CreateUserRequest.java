package com.featureflagplatform.auth.dto;

import com.featureflagplatform.auth.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** No password field — one is always generated server-side, never client-supplied. See {@link CreatedUserDto}. */
public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String displayName,
        @NotNull UserRole role) {
}
