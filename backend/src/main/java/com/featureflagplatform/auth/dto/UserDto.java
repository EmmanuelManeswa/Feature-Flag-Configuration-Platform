package com.featureflagplatform.auth.dto;

import com.featureflagplatform.auth.domain.User;
import com.featureflagplatform.auth.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

/** Never carries a password or password hash — see {@link CreatedUserDto} for the one exception (a freshly generated plaintext password, once). */
public record UserDto(UUID id, String email, String displayName, UserRole role, boolean enabled, Instant createdAt) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}
