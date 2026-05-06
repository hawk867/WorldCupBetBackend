package org.danielesteban.worldcupbetbackend.web.security;

import net.jqwik.api.*;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.service.AuthService;
import org.danielesteban.worldcupbetbackend.service.dto.JwtClaims;
import org.danielesteban.worldcupbetbackend.service.exception.AuthenticationException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Feature: web-layer
 * Property-based tests for JwtAuthenticationFilter
 */
@SuppressWarnings("unused")
class JwtAuthenticationFilterPropertyTest {

    // Feature: web-layer, Property 1: Token JWT válido establece el Principal correcto
    // **Validates: Requirements 1.1**
    @Property(tries = 100)
    void validJwtTokenSetsPrincipalCorrectly(
            @ForAll("validJwtClaims") JwtClaims claims) throws Exception {

        // Setup
        AuthService authService = mock(AuthService.class);
        String fakeToken = "valid-token-" + claims.userId();
        when(authService.validateToken(fakeToken)).thenReturn(claims);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(authService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + fakeToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        try {
            // Execute
            filter.doFilterInternal(request, response, filterChain);

            // Assert SecurityContext contains correct Authentication
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication).isInstanceOf(JwtAuthenticationToken.class);
            assertThat(authentication.isAuthenticated()).isTrue();

            // Assert principal is JwtClaims with same values
            JwtClaims principal = (JwtClaims) authentication.getPrincipal();
            assertThat(principal.userId()).isEqualTo(claims.userId());
            assertThat(principal.email()).isEqualTo(claims.email());
            assertThat(principal.role()).isEqualTo(claims.role());

            // Assert authorities include ROLE_{role}
            assertThat(authentication.getAuthorities())
                    .extracting("authority")
                    .contains("ROLE_" + claims.role().name());

            // Assert filter chain continued
            assertThat(filterChain.getRequest()).isNotNull();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // Feature: web-layer, Property 2: Token JWT inválido no establece autenticación
    // **Validates: Requirements 1.3**
    @Property(tries = 100)
    void invalidJwtTokenDoesNotSetAuthentication(
            @ForAll("invalidTokens") String invalidToken) throws Exception {

        // Setup
        AuthService authService = mock(AuthService.class);
        when(authService.validateToken(invalidToken))
                .thenThrow(new AuthenticationException("Invalid or expired token"));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(authService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + invalidToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        try {
            // Execute
            filter.doFilterInternal(request, response, filterChain);

            // Assert SecurityContext is empty (no Authentication)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();

            // Assert request continued through the filter chain
            assertThat(filterChain.getRequest()).isNotNull();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Provide
    Arbitrary<JwtClaims> validJwtClaims() {
        Arbitrary<Long> userIds = Arbitraries.longs().between(1, 10000);
        Arbitrary<String> emails = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> s.toLowerCase() + "@test.com");
        Arbitrary<UserRole> roles = Arbitraries.of(UserRole.values());

        return Combinators.combine(userIds, emails, roles)
                .as(JwtClaims::new);
    }

    @Provide
    Arbitrary<String> invalidTokens() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(200);
    }
}
