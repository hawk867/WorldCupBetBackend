package org.danielesteban.worldcupbetbackend.web.controller;

import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.danielesteban.worldcupbetbackend.domain.entity.UserScore;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.service.AuthService;
import org.danielesteban.worldcupbetbackend.service.RankingService;
import org.danielesteban.worldcupbetbackend.service.dto.JwtClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RankingController.class)
class RankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RankingService rankingService;

    @MockitoBean
    private AuthService authService;

    private static final String TOKEN = "valid-jwt-token";
    private static final JwtClaims USER_CLAIMS = new JwtClaims(1L, "user@test.com", UserRole.USER);

    @BeforeEach
    void setUp() {
        when(authService.validateToken(TOKEN)).thenReturn(USER_CLAIMS);
    }

    @Test
    void getRanking_returnsSortedList() throws Exception {
        User user1 = User.builder().id(1L).email("alice@test.com").fullName("Alice Smith")
                .role(UserRole.USER).passwordHash("hash").passwordChanged(true).build();
        User user2 = User.builder().id(2L).email("bob@test.com").fullName("Bob Jones")
                .role(UserRole.USER).passwordHash("hash").passwordChanged(true).build();

        UserScore score1 = UserScore.builder()
                .userId(1L).user(user1).totalPoints(15).exactCount(3).winnerCount(2)
                .updatedAt(Instant.now()).build();
        UserScore score2 = UserScore.builder()
                .userId(2L).user(user2).totalPoints(10).exactCount(2).winnerCount(1)
                .updatedAt(Instant.now()).build();

        when(rankingService.getRanking()).thenReturn(List.of(score1, score2));

        mockMvc.perform(get("/api/ranking")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].userId").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Alice Smith"))
                .andExpect(jsonPath("$[0].totalPoints").value(15))
                .andExpect(jsonPath("$[0].exactCount").value(3))
                .andExpect(jsonPath("$[0].winnerCount").value(2))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[1].userId").value(2))
                .andExpect(jsonPath("$[1].position").value(2));
    }
}
