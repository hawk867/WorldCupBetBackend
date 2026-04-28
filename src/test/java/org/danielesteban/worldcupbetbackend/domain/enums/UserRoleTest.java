package org.danielesteban.worldcupbetbackend.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UserRole}.
 */
class UserRoleTest {

    @Test
    @DisplayName("UserRole declares exactly USER and ADMIN, in that order")
    void enumDeclaresExactlyTheTwoDesignValues() {
        assertThat(UserRole.values())
                .containsExactly(UserRole.USER, UserRole.ADMIN);
    }
}
