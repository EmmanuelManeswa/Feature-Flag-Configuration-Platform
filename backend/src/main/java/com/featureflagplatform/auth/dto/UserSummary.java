package com.featureflagplatform.auth.dto;

import com.featureflagplatform.auth.domain.User;

import java.util.UUID;

public record UserSummary(UUID id, String email, String displayName, String role) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
    }
}
