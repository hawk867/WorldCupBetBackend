package org.danielesteban.worldcupbetbackend.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.Prediction;
import org.danielesteban.worldcupbetbackend.domain.entity.Stage;
import org.danielesteban.worldcupbetbackend.domain.entity.Team;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.service.AuthService;
import org.danielesteban.worldcupbetbackend.service.PredictionService;
import org.danielesteban.worldcupbetbackend.service.dto.JwtClaims;
import org.danielesteban.worldcupbetbackend.service.exception.DuplicatePredictionException;
import org.danielesteban.worldcupbetbackend.service.exception.ForbiddenException;
import org.danielesteban.worldcupbetbackend.service.exception.PredictionLockedException;
import org.danielesteban.worldcupbetbackend.web.dto.CreatePredictionRequest;
import org.danielesteban.worldcupbetbackend.web.dto.UpdatePredictionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PredictionController.class)
class PredictionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PredictionService predictionService;

    @MockitoBean
    private AuthService authService;

    private static final String TOKEN = "valid-jwt-token";
    private static final JwtClaims USER_CLAIMS = new JwtClaims(1L, "user@test.com", UserRole.USER);

    @BeforeEach
    void setUp() {
        when(authService.validateToken(TOKEN)).thenReturn(USER_CLAIMS);
    }

    private Prediction buildPrediction(Long id) {
        Stage stage = Stage.builder().id(1L).name("Group A").orderIdx(1).build();
        Team home = Team.builder().id(1L).name("Argentina").code("ARG").flagUrl("https://flags.com/arg.png").build();
        Team away = Team.builder().id(2L).name("Brazil").code("BRA").flagUrl("https://flags.com/bra.png").build();
        Match match = Match.builder()
                .id(10L)
                .externalId(100)
                .stage(stage)
                .homeTeam(home)
                .awayTeam(away)
                .kickoffAt(Instant.parse("2026-06-15T18:00:00Z"))
                .status(MatchStatus.SCHEDULED)
                .wentToPenalties(false)
                .build();

        return Prediction.builder()
                .id(id)
                .match(match)
                .homeGoals(2)
                .awayGoals(1)
                .homePenalties(null)
                .awayPenalties(null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void create_success_returns201() throws Exception {
        Prediction prediction = buildPrediction(1L);
        when(predictionService.create(any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(prediction);

        CreatePredictionRequest request = new CreatePredictionRequest(10L, 2, 1, null, null);

        mockMvc.perform(post("/api/predictions")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.homeGoals").value(2))
                .andExpect(jsonPath("$.awayGoals").value(1));
    }

    @Test
    void create_lockedMatch_returns409() throws Exception {
        when(predictionService.create(any(), any(), anyInt(), anyInt(), any(), any()))
                .thenThrow(new PredictionLockedException("Predictions are locked for match: 10"));

        CreatePredictionRequest request = new CreatePredictionRequest(10L, 2, 1, null, null);

        mockMvc.perform(post("/api/predictions")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void create_duplicate_returns409() throws Exception {
        when(predictionService.create(any(), any(), anyInt(), anyInt(), any(), any()))
                .thenThrow(new DuplicatePredictionException("Prediction already exists for match: 10"));

        CreatePredictionRequest request = new CreatePredictionRequest(10L, 2, 1, null, null);

        mockMvc.perform(post("/api/predictions")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void update_success_returns200() throws Exception {
        Prediction prediction = buildPrediction(1L);
        when(predictionService.update(any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(prediction);

        UpdatePredictionRequest request = new UpdatePredictionRequest(3, 0, null, null);

        mockMvc.perform(put("/api/predictions/1")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void update_otherUser_returns403() throws Exception {
        when(predictionService.update(any(), any(), anyInt(), anyInt(), any(), any()))
                .thenThrow(new ForbiddenException("Prediction does not belong to user: 1"));

        UpdatePredictionRequest request = new UpdatePredictionRequest(3, 0, null, null);

        mockMvc.perform(put("/api/predictions/1")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyPredictions_returnsList() throws Exception {
        Prediction prediction = buildPrediction(1L);
        when(predictionService.findByUser(any())).thenReturn(List.of(prediction));

        mockMvc.perform(get("/api/predictions")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getByMatch_noPrediction_returns204() throws Exception {
        when(predictionService.findByUserAndMatch(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/predictions/match/1")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNoContent());
    }
}
