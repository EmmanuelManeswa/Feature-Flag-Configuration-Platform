package com.featureflagplatform.auth.domain;

/**
 * Two roles, matching the assessment brief exactly. A users/roles/user_roles
 * join model was considered and rejected — nothing in this project needs a
 * user to hold more than one role or roles to have their own metadata, so
 * that would have been unused complexity. See
 * .claude/decisions/ADR-004-simple-roles.md.
 */
public enum UserRole {
    ADMIN,
    VIEWER
}
