package org.danielesteban.worldcupbetbackend.web.security;

import org.danielesteban.worldcupbetbackend.service.dto.JwtClaims;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Token de autenticación personalizado que envuelve JwtClaims como principal.
 * Implementa Authentication de Spring Security mapeando el UserRole a una
 * SimpleGrantedAuthority con prefijo ROLE_.
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final JwtClaims claims;

    public JwtAuthenticationToken(JwtClaims claims) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
        this.claims = claims;
        setAuthenticated(true);
    }

    @Override
    public Object getPrincipal() {
        return claims;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
