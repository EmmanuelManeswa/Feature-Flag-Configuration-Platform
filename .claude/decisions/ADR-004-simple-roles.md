# ADR-004: Single-column role instead of users/roles/user_roles

**Status:** Accepted

## Context

The assessment asks for exactly two roles — ADMIN and VIEWER — with ADMIN
allowed to mutate configuration and VIEWER limited to reading and evaluating.
The "textbook" RBAC schema is a `users` / `roles` / `user_roles` join model
so a user can hold multiple roles and roles can carry their own metadata.

## Decision

`users.role` is a single `VARCHAR` column constrained to `ADMIN`/`VIEWER`,
mapped to a plain Java enum (`UserRole`). No `roles` or `user_roles` table.

## Why

- Nothing in this domain needs a user to hold more than one role
  simultaneously, and nothing needs roles to have independent attributes
  (descriptions, hierarchies, per-role permission sets). The join-table
  version would be schema and code that exists to be generic, not because
  anything in this project uses the generality — exactly the kind of
  premature abstraction `.claude/CLAUDE.md` says to avoid.
- Spring Security still gets a normal `GrantedAuthority` (`ROLE_ADMIN` /
  `ROLE_VIEWER`) out of this column at authentication time, so
  `@PreAuthorize("hasRole('ADMIN')")` reads and works identically to the
  join-table version — the simplification is invisible to the authorization
  code that actually enforces the boundary.

## What would change this

If a future requirement needed a user to hold multiple roles at once, or
roles to be data-driven (created/renamed by an admin at runtime rather than
being a fixed enum), the join-table model would earn its complexity back.
Neither is in scope here.
