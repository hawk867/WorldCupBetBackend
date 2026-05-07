package org.danielesteban.worldcupbetbackend.web.controller;

import jakarta.validation.Valid;
import org.danielesteban.worldcupbetbackend.service.AuthService;
import org.danielesteban.worldcupbetbackend.service.dto.JwtClaims;
import org.danielesteban.worldcupbetbackend.web.dto.ChangePasswordRequest;
import org.danielesteban.worldcupbetbackend.web.dto.LoginRequest;
import org.danielesteban.worldcupbetbackend.web.dto.LoginResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        String token = authService.login(request.email(), request.password());
        return ResponseEntity.ok(new LoginResponse(token));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal JwtClaims principal,
            @RequestBody @Valid ChangePasswordRequest request) {
        authService.changePassword(principal.userId(),
                request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
