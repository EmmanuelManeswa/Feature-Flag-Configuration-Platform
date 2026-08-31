package com.featureflagplatform.auth.dto;

/**
 * Returned exactly once, from {@code POST /api/v1/users} — {@code generatedPassword} is never
 * retrievable again after this response; only its bcrypt hash is persisted. The admin creating
 * the account must copy it now and share it with the new user out of band — there is no
 * password-reset-by-email flow in this demo-scoped feature; the new user can change their own
 * password after logging in via {@code PUT /api/v1/auth/me/password}.
 */
public record CreatedUserDto(UserDto user, String generatedPassword) {
}
