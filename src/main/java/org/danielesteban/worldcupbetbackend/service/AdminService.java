package org.danielesteban.worldcupbetbackend.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.danielesteban.worldcupbetbackend.domain.entity.AuditLog;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.danielesteban.worldcupbetbackend.domain.entity.UserScore;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.persistence.repository.AuditLogRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.MatchRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.UserRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.UserScoreRepository;
import org.danielesteban.worldcupbetbackend.service.dto.CsvRowError;
import org.danielesteban.worldcupbetbackend.service.dto.CsvUploadResult;
import org.danielesteban.worldcupbetbackend.service.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final UserScoreRepository userScoreRepository;
    private final MatchRepository matchRepository;
    private final AuditLogRepository auditLogRepository;
    private final MatchService matchService;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository,
                        UserScoreRepository userScoreRepository,
                        MatchRepository matchRepository,
                        AuditLogRepository auditLogRepository,
                        MatchService matchService,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userScoreRepository = userScoreRepository;
        this.matchRepository = matchRepository;
        this.auditLogRepository = auditLogRepository;
        this.matchService = matchService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public CsvUploadResult uploadUsers(Long adminId, InputStream csvStream) {
        User admin = findAdmin(adminId);
        List<CsvRowError> errors = new ArrayList<>();
        int createdCount = 0;
        int rowNumber = 1;

        try {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .builder()
                    .setHeader("email", "fullName", "password")
                    .setSkipHeaderRecord(true)
                    .build()
                    .parse(new InputStreamReader(csvStream, StandardCharsets.UTF_8));

            for (CSVRecord record : records) {
                String email = record.get("email").trim();
                String fullName = record.get("fullName").trim();
                String password = record.get("password").trim();

                if (email.isEmpty()) {
                    errors.add(new CsvRowError(rowNumber, email, "Email is required"));
                } else if (fullName.isEmpty()) {
                    errors.add(new CsvRowError(rowNumber, email, "Full name is required"));
                } else if (password.isEmpty()) {
                    errors.add(new CsvRowError(rowNumber, email, "Password is required"));
                } else if (userRepository.existsByEmail(email)) {
                    errors.add(new CsvRowError(rowNumber, email, "Email already exists"));
                } else {
                    User user = userRepository.save(User.builder()
                            .email(email)
                            .fullName(fullName)
                            .passwordHash(passwordEncoder.encode(password))
                            .role(UserRole.USER)
                            .passwordChanged(false)
                            .build());

                    userScoreRepository.save(UserScore.builder()
                            .userId(user.getId())
                            .user(user)
                            .totalPoints(0)
                            .exactCount(0)
                            .winnerCount(0)
                            .build());

                    createdCount++;
                }
                rowNumber++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse CSV", e);
        }

        audit(admin, "UPLOAD_USERS", "User", null,
                Map.of("createdCount", createdCount, "errorCount", errors.size()));

        return new CsvUploadResult(createdCount, errors);
    }

    @Transactional
    public void resetPassword(Long adminId, Long targetUserId, String newPassword) {
        User admin = findAdmin(adminId);
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

        target.setPasswordHash(passwordEncoder.encode(newPassword));
        target.setPasswordChanged(false);

        audit(admin, "RESET_PASSWORD", "User", targetUserId, Map.of("targetUserId", targetUserId));
    }

    @Transactional
    public Match adjustResult(Long adminId, Long matchId,
                              int homeGoals, int awayGoals,
                              Integer homePenalties, Integer awayPenalties) {
        User admin = findAdmin(adminId);
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));

        if (match.getStatus() != MatchStatus.FINISHED && match.getStatus() != MatchStatus.ADJUSTED) {
            throw new IllegalStateException("Match must be FINISHED or ADJUSTED to adjust result");
        }

        match.setHomeGoals(homeGoals);
        match.setAwayGoals(awayGoals);
        match.setHomePenalties(homePenalties);
        match.setAwayPenalties(awayPenalties);
        match.setWentToPenalties(homePenalties != null && awayPenalties != null);

        matchService.transitionStatus(matchId, MatchStatus.ADJUSTED);

        audit(admin, "ADJUST_RESULT", "Match", matchId,
                Map.of("homeGoals", homeGoals, "awayGoals", awayGoals));

        return match;
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLog() {
        return auditLogRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogByEntity(String entity, Long entityId) {
        return auditLogRepository.findAllByEntityAndEntityIdOrderByCreatedAtDesc(entity, entityId);
    }

    private User findAdmin(Long adminId) {
        return userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found: " + adminId));
    }

    private void audit(User admin, String action, String entity, Long entityId, Map<String, Object> details) {
        auditLogRepository.save(AuditLog.builder()
                .admin(admin)
                .action(action)
                .entity(entity)
                .entityId(entityId)
                .details(details)
                .build());
    }
}
