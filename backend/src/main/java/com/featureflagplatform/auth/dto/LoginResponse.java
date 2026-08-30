package com.featureflagplatform.auth.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, UserSummary user) {
}
