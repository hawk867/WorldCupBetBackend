package org.danielesteban.worldcupbetbackend.service;

import net.jqwik.api.*;
import net.jqwik.api.Combinators;
import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.persistence.repository.*;
import org.danielesteban.worldcupbetbackend.service.dto.CsvUploadResult;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Feature: service-layer
 * Properties 16-18: AdminService correctness properties
 */
@SuppressWarnings("unused")
class AdminServicePropertyTest {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private static final User ADMIN = User.builder()
            .id(1L).email("admin@test.com").passwordHash("x")
            .fullName("Admin").role(UserRole.ADMIN).build();

    private AdminService buildService(UserRepository userRepo, UserScoreRepository userScoreRepo) {
        return new AdminService(
                userRepo, userScoreRepo,
                mock(MatchRepository.class),
                mock(AuditLogRepository.class),
                mock(MatchService.class),
                PASSWORD_ENCODER
        );
    }

    // Property 16: Correctitud de carga CSV
    @Property(tries = 100)
    void csvUploadCreatedPlusErrorsEqualsTotal(
            @ForAll("csvRows") List<String[]> rows) {

        UserRepository userRepo = mock(UserRepository.class);
        UserScoreRepository userScoreRepo = mock(UserScoreRepository.class);
        when(userRepo.findById(1L)).thenReturn(Optional.of(ADMIN));

        // Simulamos que emails que terminan en "dup" ya existen
        when(userRepo.existsByEmail(anyString())).thenAnswer(inv -> {
            String email = inv.getArgument(0);
            return email.startsWith("dup");
        });
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u = User.builder().id(ID_SEQ.getAndIncrement()).email(u.getEmail())
                    .fullName(u.getFullName()).passwordHash(u.getPasswordHash())
                    .role(u.getRole()).passwordChanged(u.isPasswordChanged()).build();
            return u;
        });
        when(userScoreRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String csv = buildCsv(rows);
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        CsvUploadResult result = buildService(userRepo, userScoreRepo).uploadUsers(1L, stream);

        assertThat(result.createdCount() + result.errors().size()).isEqualTo(rows.size());
    }

    // Property 17: Contraseñas siempre hasheadas
    @Property(tries = 100)
    void passwordsAreAlwaysHashed(@ForAll("validPasswords") String password) {
        UserRepository userRepo = mock(UserRepository.class);
        UserScoreRepository userScoreRepo = mock(UserScoreRepository.class);
        when(userRepo.findById(1L)).thenReturn(Optional.of(ADMIN));
        when(userRepo.existsByEmail(anyString())).thenReturn(false);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return User.builder().id(ID_SEQ.getAndIncrement()).email(u.getEmail())
                    .fullName(u.getFullName()).passwordHash(u.getPasswordHash())
                    .role(u.getRole()).passwordChanged(u.isPasswordChanged()).build();
        });
        when(userScoreRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String csv = "email,fullName,password\nuser@test.com,Test User," + password + "\n";
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        buildService(userRepo, userScoreRepo).uploadUsers(1L, stream);

        verify(userRepo).save(argThat(u -> {
            assertThat(u.getPasswordHash()).isNotEqualTo(password);
            assertThat(PASSWORD_ENCODER.matches(password, u.getPasswordHash())).isTrue();
            return true;
        }));
    }

    // Property 18: Inicialización de UserScore en cero
    @Property(tries = 100)
    void userScoreInitializedToZero(@ForAll("validPasswords") String password) {
        UserRepository userRepo = mock(UserRepository.class);
        UserScoreRepository userScoreRepo = mock(UserScoreRepository.class);
        when(userRepo.findById(1L)).thenReturn(Optional.of(ADMIN));
        when(userRepo.existsByEmail(anyString())).thenReturn(false);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return User.builder().id(ID_SEQ.getAndIncrement()).email(u.getEmail())
                    .fullName(u.getFullName()).passwordHash(u.getPasswordHash())
                    .role(u.getRole()).passwordChanged(u.isPasswordChanged()).build();
        });
        when(userScoreRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String csv = "email,fullName,password\nuser2@test.com,Test User," + password + "\n";
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        buildService(userRepo, userScoreRepo).uploadUsers(1L, stream);

        verify(userScoreRepo).save(argThat(us -> {
            assertThat(us.getTotalPoints()).isEqualTo(0);
            assertThat(us.getExactCount()).isEqualTo(0);
            assertThat(us.getWinnerCount()).isEqualTo(0);
            return true;
        }));
    }

    private String buildCsv(List<String[]> rows) {
        StringBuilder sb = new StringBuilder("email,fullName,password\n");
        for (String[] row : rows) {
            sb.append(String.join(",", row)).append("\n");
        }
        return sb.toString();
    }

    @Provide
    Arbitrary<List<String[]>> csvRows() {
        Arbitrary<String[]> validRow = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8)
        ).as((name, suffix) -> new String[]{
                name + "@test.com", "User " + name, "password123"
        });

        Arbitrary<String[]> dupRow = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8)
                .map(name -> new String[]{"dup" + name + "@test.com", "User " + name, "password123"});

        Arbitrary<String[]> invalidRow = Arbitraries.of(
                new String[]{"", "No Email", "password123"},
                new String[]{"nofullname@test.com", "", "password123"}
        );

        return Arbitraries.oneOf(validRow, dupRow, invalidRow)
                .list().ofMinSize(0).ofMaxSize(10);
    }

    @Provide
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings().ofMinLength(8).ofMaxLength(32).alpha();
    }
}
