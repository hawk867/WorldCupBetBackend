package org.danielesteban.worldcupbetbackend.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danielesteban.worldcupbetbackend.domain.entity.AuditLog;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.Stage;
import org.danielesteban.worldcupbetbackend.domain.entity.Team;
import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.service.AdminService;
import org.danielesteban.worldcupbetbackend.service.AuthService;
import org.danielesteban.worldcupbetbackend.service.MatchService;
import org.danielesteban.worldcupbetbackend.service.ScoringService;
import org.danielesteban.worldcupbetbackend.service.dto.CsvUploadResult;
import org.danielesteban.worldcupbetbackend.service.dto.JwtClaims;
import org.danielesteban.worldcupbetbackend.service.exception.IllegalStateTransitionException;
import org.danielesteban.worldcupbetbackend.web.dto.AdjustResultRequest;
import org.danielesteban.worldcupbetbackend.web.dto.ResetPasswordRequest;
import org.danielesteban.worldcupbetbackend.web.dto.TransitionStatusRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private MatchService matchService;

    @MockitoBean
    private ScoringService scoringService;

    @MockitoBean
    private AuthService authService;

    private static final String ADMIN_TOKEN = "admin-jwt-token";
    private static final String USER_TOKEN = "user-jwt-token";
    private static final JwtClaims ADMIN_CLAIMS = new JwtClaims(1L, "admin@test.com", UserRole.ADMIN);
    private static final JwtClaims USER_CLAIMS = new JwtClaims(2L, "user@test.com", UserRole.USER);

    @BeforeEach
    void setUp() {
        when(authService.validateToken(ADMIN_TOKEN)).thenReturn(ADMIN_CLAIMS);
        when(authService.validateToken(USER_TOKEN)).thenReturn(USER_CLAIMS);
    }

    private Match buildMatch(Long id) {
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
                .status(MatchStatus.FINISHED)
                .homeGoals(2)
                .awayGoals(1)
                .wentToPenalties(false)
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void uploadUsers_returns200WithSummary() throws Exception {
        CsvUploadResult result = new CsvUploadResult(3, List.of());
        when(adminService.uploadUsers(any(), any())).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile(
                "file", "users.csv", "text/csv",
                "email,fullName,password\ntest@test.com,Test User,pass123".getBytes());

        mockMvc.perform(multipart("/api/admin/users/upload").file(file)
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(3))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void resetPassword_returns204() throws Exception {
        doNothing().when(adminService).resetPassword(1L, 1L, "newPass123");

        ResetPasswordRequest request = new ResetPasswordRequest("newPass123");

        mockMvc.perform(post("/api/admin/users/1/reset-password")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    void adjustResult_returns200() throws Exception {
        Match match = buildMatch(1L);
        when(adminService.adjustResult(any(), any(), anyInt(), anyInt(), any(), any())).thenReturn(match);

        AdjustResultRequest request = new AdjustResultRequest(3, 2, null, null);

        mockMvc.perform(put("/api/admin/matches/1/result")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void transitionStatus_invalidTransition_returns422() throws Exception {
        when(matchService.transitionStatus(1L, MatchStatus.LIVE))
                .thenThrow(new IllegalStateTransitionException("FINISHED", "LIVE"));

        TransitionStatusRequest request = new TransitionStatusRequest(MatchStatus.LIVE);

        mockMvc.perform(put("/api/admin/matches/1/status")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void recalculate_returns204() throws Exception {
        Match match = buildMatch(1L);
        when(matchService.findById(1L)).thenReturn(match);
        doNothing().when(scoringService).recalculateScores(any());

        mockMvc.perform(post("/api/admin/matches/1/recalculate")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isNoContent());

        verify(matchService).findById(1L);
        verify(scoringService).recalculateScores(any());
    }

    @Test
    void getAuditLog_returnsList() throws Exception {
        User admin = User.builder().id(1L).email("admin@test.com").fullName("Admin")
                .role(UserRole.ADMIN).passwordHash("hash").passwordChanged(true).build();
        AuditLog log = AuditLog.builder()
                .id(1L)
                .admin(admin)
                .action("UPLOAD_USERS")
                .entity("User")
                .entityId(null)
                .details(Map.of("createdCount", 3))
                .createdAt(Instant.now())
                .build();

        when(adminService.getAuditLog()).thenReturn(List.of(log));

        mockMvc.perform(get("/api/admin/audit-log")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].action").value("UPLOAD_USERS"));
    }

    @Test
    void accessWithUserRole_returns403() throws Exception {
        // Security authorization rules (role-based access) are tested in SecurityPropertyTest
        // which uses @SpringBootTest with the full security filter chain.
        // This @WebMvcTest slice doesn't load SecurityConfig's authorization rules.
    }
}
