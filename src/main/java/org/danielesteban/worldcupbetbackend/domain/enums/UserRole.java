package org.danielesteban.worldcupbetbackend.domain.enums;

/**
 * User privilege level.
 * <p>
 * Stored in the {@code users.role} column as a {@code VARCHAR} via
 * {@code @Enumerated(EnumType.STRING)} and guarded at the database level by
 * the {@code chk_users_role} check constraint. Adding a new value requires a
 * Flyway migration widening that constraint before the enum change is
 * deployed.
 */
public enum UserRole {
    /** Regular participant; can create and update their own predictions. */
    USER,
    /** Administrator; can manage users, matches, and view the audit log. */
    ADMIN
}
