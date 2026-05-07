package org.danielesteban.worldcupbetbackend.web.controller;

import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.Stage;
import org.danielesteban.worldcupbetbackend.domain.entity.Team;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.service.AuthService;
import org.danielesteban.worldcupbetbackend.service.MatchService;
import org.danielesteban.worldcupbetbackend.service.dto.JwtClaims;
import org.danielesteban.worldcupbetbackend.service.exception.ResourceNotFoundException;
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

@WebMvcTest(MatchController.class)
class MatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchService matchService;

    @MockitoBean
    private AuthService authService;

    private static final String TOKEN = "valid-jwt-token";
    private static final JwtClaims USER_CLAIMS = new JwtClaims(1L, "user@test.com", UserRole.USER);

    @BeforeEach
    void setUp() {
        when(authService.validateToken(TOKEN)).thenReturn(USER_CLAIMS);
    }

    private Match buildMatch(Long id, MatchStatus status) {
        Stage stage = Stage.builder().id(1L).name("Group A").orderIdx(1).build();
        Team home = Team.builder().id(1L).name("Argentina").code("ARG").flagUrl("https://flags.com/arg.png").build();
        Team away = Team.builder().id(2L).name("Brazil").code("BRA").flagUrl("https://flags.com/bra.png").build();

        return Match.builder()
                .id(id)
                .externalId(100)
                .stage(stage)
                .homeTeam(home)
                .awayTeam(away)
                .kickoffAt(Instant.parse("2026-06-15T18:00:00Z"))
                .status(status)
                .homeGoals(null)
                .awayGoals(null)
                .homePenalties(null)
                .awayPenalties(null)
                .wentToPenalties(false)
                .build();
    }

    @Test
    void getMatches_returnsAllMatches() throws Exception {
        Match match = buildMatch(1L, MatchStatus.SCHEDULED);
        when(matchService.findAll()).thenReturn(List.of(match));

        mockMvc.perform(get("/api/matches")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].homeTeam").value("Argentina"))
                .andExpect(jsonPath("$[0].awayTeam").value("Brazil"));
    }

    @Test
    void getMatches_filteredByStage() throws Exception {
        Match match = buildMatch(1L, MatchStatus.SCHEDULED);
        when(matchService.findByStage(1L)).thenReturn(List.of(match));

        mockMvc.perform(get("/api/matches").param("stageId", "1")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(matchService).findByStage(1L);
    }

    @Test
    void getMatches_filteredByStatus() throws Exception {
        Match match = buildMatch(1L, MatchStatus.LIVE);
        when(matchService.findByStatus(MatchStatus.LIVE)).thenReturn(List.of(match));

        mockMvc.perform(get("/api/matches").param("status", "LIVE")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(matchService).findByStatus(MatchStatus.LIVE);
    }

    @Test
    void getMatch_existingId_returns200() throws Exception {
        Match match = buildMatch(1L, MatchStatus.SCHEDULED);
        when(matchService.findById(1L)).thenReturn(match);

        mockMvc.perform(get("/api/matches/1")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.homeTeam.name").value("Argentina"))
                .andExpect(jsonPath("$.awayTeam.name").value("Brazil"));
    }

    @Test
    void getMatch_nonExistingId_returns404() throws Exception {
        when(matchService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Match not found: 99"));

        mockMvc.perform(get("/api/matches/99")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }
}
