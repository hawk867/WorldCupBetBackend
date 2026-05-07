package org.danielesteban.worldcupbetbackend.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.service.AuthService;
import org.danielesteban.worldcupbetbackend.service.dto.JwtClaims;
import org.danielesteban.worldcupbetbackend.service.exception.AuthenticationException;
import org.danielesteban.worldcupbetbackend.web.dto.ChangePasswordRequest;
import org.danielesteban.worldcupbetbackend.web.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    private static final String VALID_TOKEN = "valid-jwt-token";
    private static final JwtClaims USER_CLAIMS = new JwtClaims(1L, "user@test.com", UserRole.USER);

    @Test
    void login_withValidCredentials_returns200WithToken() throws Exception {
        when(authService.login("user@test.com", "password123"))
                .thenReturn("jwt-token");

        LoginRequest request = new LoginRequest("user@test.com", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void login_withInvalidCredentials_returns401() throws Exception {
        when(authService.login("user@test.com", "wrong"))
                .thenThrow(new AuthenticationException("Invalid credentials"));

        LoginRequest request = new LoginRequest("user@test.com", "wrong");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_success_returns204() throws Exception {
        when(authService.validateToken(VALID_TOKEN)).thenReturn(USER_CLAIMS);
        doNothing().when(authService).changePassword(any(), any(), any());

        ChangePasswordRequest request = new ChangePasswordRequest("oldPass", "newPass123");

        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).changePassword(any(), any(), any());
    }

    @Test
    void changePassword_wrongCurrentPassword_returns401() throws Exception {
        when(authService.validateToken(VALID_TOKEN)).thenReturn(USER_CLAIMS);
        doThrow(new AuthenticationException("Invalid credentials"))
                .when(authService).changePassword(any(), any(), any());

        ChangePasswordRequest request = new ChangePasswordRequest("wrongPass", "newPass123");

        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
