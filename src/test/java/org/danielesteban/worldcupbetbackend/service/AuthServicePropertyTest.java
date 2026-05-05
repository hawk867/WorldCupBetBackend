package org.danielesteban.worldcupbetbackend.service;

import net.jqwik.api.*;
import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.persistence.repository.UserRepository;
import org.danielesteban.worldcupbetbackend.service.dto.JwtClaims;
import org.danielesteban.worldcupbetbackend.service.exception.AuthenticationException;
import org.danielesteban.worldcupbetbackend.service.exception.ValidationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Feature: service-layer
 * Properties 1-4: AuthService correctness properties
 */
@SuppressWarnings("unused")
class AuthServicePropertyTest {

    private static final String JWT_SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256";
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private AuthService buildService(UserRepository repo) {
        return new AuthService(repo, PASSWORD_ENCODER, JWT_SECRET, 86400000L);
    }

    private User userWithPassword(long id, String email, String rawPassword, UserRole role) {
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash(PASSWORD_ENCODER.encode(rawPassword))
                .fullName("Test User")
                .role(role)
                .passwordChanged(false)
                .build();
    }

    // Property 1: Round-trip de JWT (login produce token con claims correctos)
    @Property(tries = 100)
    void jwtRoundTrip(
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password,
            @ForAll("userRoles") UserRole role) {

        User user = userWithPassword(42L, email, password, role);
        UserRepository repo = mock(UserRepository.class);
        when(repo.findByEmail(email)).thenReturn(Optional.of(user));

        AuthService service = buildService(repo);
        String token = service.login(email, password);
        JwtClaims claims = service.validateToken(token);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.email()).isEqualTo(email);
        assertThat(claims.role()).isEqualTo(role);
    }

    // Property 2: Credenciales inválidas producen error uniforme
    @Property(tries = 100)
    void invalidCredentialsProduceUniformError(
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String correctPassword,
            @ForAll("validPasswords") String wrongPassword) {

        Assume.that(!correctPassword.equals(wrongPassword));

        User user = userWithPassword(1L, email, correctPassword, UserRole.USER);
        UserRepository repoWithUser = mock(UserRepository.class);
        when(repoWithUser.findByEmail(email)).thenReturn(Optional.of(user));

        UserRepository repoEmpty = mock(UserRepository.class);
        when(repoEmpty.findByEmail(any())).thenReturn(Optional.empty());

        AuthService serviceWithUser = buildService(repoWithUser);
        AuthService serviceEmpty = buildService(repoEmpty);

        // email correcto, contraseña incorrecta
        assertThatThrownBy(() -> serviceWithUser.login(email, wrongPassword))
                .isInstanceOf(AuthenticationException.class);

        // email inexistente
        assertThatThrownBy(() -> serviceEmpty.login("nonexistent@example.com", correctPassword))
                .isInstanceOf(AuthenticationException.class);
    }

    // Property 3: Cambio de contraseña actualiza hash y flag
    @Property(tries = 100)
    void changePasswordUpdatesHashAndFlag(
            @ForAll("validPasswords") String currentPassword,
            @ForAll("validPasswords") String newPassword) {

        User user = userWithPassword(1L, "user@example.com", currentPassword, UserRole.USER);
        UserRepository repo = mock(UserRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(user));

        String oldHash = user.getPasswordHash();
        buildService(repo).changePassword(1L, currentPassword, newPassword);

        assertThat(user.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(PASSWORD_ENCODER.matches(newPassword, user.getPasswordHash())).isTrue();
        assertThat(user.isPasswordChanged()).isTrue();
    }

    // Property 4: Contraseñas cortas son rechazadas
    @Property(tries = 100)
    void shortPasswordsAreRejected(
            @ForAll("validPasswords") String currentPassword,
            @ForAll("shortPasswords") String shortPassword) {

        User user = userWithPassword(1L, "user@example.com", currentPassword, UserRole.USER);
        UserRepository repo = mock(UserRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(user));

        String hashBefore = user.getPasswordHash();

        assertThatThrownBy(() -> buildService(repo).changePassword(1L, currentPassword, shortPassword))
                .isInstanceOf(ValidationException.class);

        assertThat(user.getPasswordHash()).isEqualTo(hashBefore);
    }

    @Provide
    Arbitrary<String> validEmails() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> s + "@example.com");
    }

    @Provide
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings().ofMinLength(8).ofMaxLength(32).alpha();
    }

    @Provide
    Arbitrary<String> shortPasswords() {
        return Arbitraries.strings().ofMinLength(0).ofMaxLength(7);
    }

    @Provide
    Arbitrary<UserRole> userRoles() {
        return Arbitraries.of(UserRole.values());
    }
}
