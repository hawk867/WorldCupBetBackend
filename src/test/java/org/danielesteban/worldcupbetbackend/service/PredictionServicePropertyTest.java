package org.danielesteban.worldcupbetbackend.service;

import net.jqwik.api.*;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.Prediction;
import org.danielesteban.worldcupbetbackend.domain.entity.Stage;
import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.domain.enums.UserRole;
import org.danielesteban.worldcupbetbackend.persistence.repository.MatchRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.PredictionRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.UserRepository;
import org.danielesteban.worldcupbetbackend.service.exception.DuplicatePredictionException;
import org.danielesteban.worldcupbetbackend.service.exception.ForbiddenException;
import org.danielesteban.worldcupbetbackend.service.exception.PredictionLockedException;
import org.danielesteban.worldcupbetbackend.service.exception.ValidationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Feature: service-layer
 * Properties 6-11: PredictionService correctness properties
 */
class PredictionServicePropertyTest {

    private static final User USER_1 = User.builder().id(1L).role(UserRole.USER).email("a@a.com").passwordHash("x").fullName("A").build();
    private static final User USER_2 = User.builder().id(2L).role(UserRole.USER).email("b@b.com").passwordHash("x").fullName("B").build();

    private PredictionService buildService(MatchRepository matchRepo,
                                           PredictionRepository predRepo,
                                           UserRepository userRepo) {
        return new PredictionService(predRepo, matchRepo, userRepo);
    }

    private Match matchWithStatus(MatchStatus status, int stageOrderIdx) {
        Stage stage = Stage.builder().id(1L).name("S").orderIdx(stageOrderIdx).build();
        return Match.builder().id(10L).status(status).stage(stage)
                .homeGoals(null).awayGoals(null).build();
    }

    // Property 6: Bloqueo de predicciones por estado del partido
    @Property(tries = 100)
    void predictionLockedWhenMatchIsLocked(@ForAll("lockedStatuses") MatchStatus status) {
        Match match = matchWithStatus(status, 1);
        MatchRepository matchRepo = mock(MatchRepository.class);
        PredictionRepository predRepo = mock(PredictionRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        when(matchRepo.findById(10L)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> buildService(matchRepo, predRepo, userRepo)
                .create(1L, 10L, 1, 0, null, null))
                .isInstanceOf(PredictionLockedException.class);
    }

    @Property(tries = 100)
    void predictionAllowedWhenMatchIsUnlocked(@ForAll("unlockedStatuses") MatchStatus status) {
        Match match = matchWithStatus(status, 1);
        MatchRepository matchRepo = mock(MatchRepository.class);
        PredictionRepository predRepo = mock(PredictionRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        when(matchRepo.findById(10L)).thenReturn(Optional.of(match));
        when(predRepo.existsByUserIdAndMatchId(1L, 10L)).thenReturn(false);
        when(userRepo.findById(1L)).thenReturn(Optional.of(USER_1));
        when(predRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Prediction result = buildService(matchRepo, predRepo, userRepo)
                .create(1L, 10L, 1, 0, null, null);

        assertThat(result).isNotNull();
    }

    // Property 7: Unicidad de predicción por usuario-partido
    @Property(tries = 100)
    void duplicatePredictionIsRejected(@ForAll("validGoals") int home, @ForAll("validGoals") int away) {
        Match match = matchWithStatus(MatchStatus.SCHEDULED, 1);
        MatchRepository matchRepo = mock(MatchRepository.class);
        PredictionRepository predRepo = mock(PredictionRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        when(matchRepo.findById(10L)).thenReturn(Optional.of(match));
        when(predRepo.existsByUserIdAndMatchId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> buildService(matchRepo, predRepo, userRepo)
                .create(1L, 10L, home, away, null, null))
                .isInstanceOf(DuplicatePredictionException.class);

        verify(predRepo, never()).save(any());
    }

    // Property 8: Goles negativos son rechazados
    @Property(tries = 100)
    void negativeGoalsAreRejected(@ForAll("negativeGoals") int negGoal, @ForAll("validGoals") int other) {
        MatchRepository matchRepo = mock(MatchRepository.class);
        PredictionRepository predRepo = mock(PredictionRepository.class);
        UserRepository userRepo = mock(UserRepository.class);

        assertThatThrownBy(() -> buildService(matchRepo, predRepo, userRepo)
                .create(1L, 10L, negGoal, other, null, null))
                .isInstanceOf(ValidationException.class);

        assertThatThrownBy(() -> buildService(matchRepo, predRepo, userRepo)
                .create(1L, 10L, other, negGoal, null, null))
                .isInstanceOf(ValidationException.class);

        verify(predRepo, never()).save(any());
    }

    // Property 9: Propiedad de la predicción (ownership)
    @Property(tries = 100)
    void updateByNonOwnerIsRejected(@ForAll("validGoals") int home, @ForAll("validGoals") int away) {
        Match match = matchWithStatus(MatchStatus.SCHEDULED, 1);
        Prediction existing = Prediction.builder().id(5L).user(USER_1).match(match)
                .homeGoals(1).awayGoals(0).build();

        PredictionRepository predRepo = mock(PredictionRepository.class);
        when(predRepo.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> buildService(mock(MatchRepository.class), predRepo, mock(UserRepository.class))
                .update(USER_2.getId(), 5L, home, away, null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    // Property 10: Manejo de penaltis según etapa y empate
    @Property(tries = 100)
    void penaltiesStoredOnlyInKnockoutWithDraw(
            @ForAll("validGoals") int goals,
            @ForAll("differentPenalties") int[] penalties) {

        // Fase de grupos: penaltis ignorados aunque haya empate
        Match groupMatch = matchWithStatus(MatchStatus.SCHEDULED, 1);
        MatchRepository matchRepo = mock(MatchRepository.class);
        PredictionRepository predRepo = mock(PredictionRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        when(matchRepo.findById(10L)).thenReturn(Optional.of(groupMatch));
        when(predRepo.existsByUserIdAndMatchId(1L, 10L)).thenReturn(false);
        when(userRepo.findById(1L)).thenReturn(Optional.of(USER_1));
        when(predRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Prediction groupResult = buildService(matchRepo, predRepo, userRepo)
                .create(1L, 10L, goals, goals, penalties[0], penalties[1]);
        assertThat(groupResult.getHomePenalties()).isNull();
        assertThat(groupResult.getAwayPenalties()).isNull();

        // Fase eliminatoria con empate: penaltis aceptados
        Match knockoutMatch = matchWithStatus(MatchStatus.SCHEDULED, 2);
        MatchRepository matchRepo2 = mock(MatchRepository.class);
        PredictionRepository predRepo2 = mock(PredictionRepository.class);
        UserRepository userRepo2 = mock(UserRepository.class);
        when(matchRepo2.findById(10L)).thenReturn(Optional.of(knockoutMatch));
        when(predRepo2.existsByUserIdAndMatchId(1L, 10L)).thenReturn(false);
        when(userRepo2.findById(1L)).thenReturn(Optional.of(USER_1));
        when(predRepo2.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Prediction knockoutResult = buildService(matchRepo2, predRepo2, userRepo2)
                .create(1L, 10L, goals, goals, penalties[0], penalties[1]);
        assertThat(knockoutResult.getHomePenalties()).isEqualTo(penalties[0]);
        assertThat(knockoutResult.getAwayPenalties()).isEqualTo(penalties[1]);
    }

    // Property 11: Penaltis empatados son rechazados
    @Property(tries = 100)
    void tiedPenaltiesAreRejected(@ForAll("validGoals") int goals, @ForAll("validGoals") int penScore) {
        Match match = matchWithStatus(MatchStatus.SCHEDULED, 2);
        MatchRepository matchRepo = mock(MatchRepository.class);
        PredictionRepository predRepo = mock(PredictionRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        when(matchRepo.findById(10L)).thenReturn(Optional.of(match));
        when(predRepo.existsByUserIdAndMatchId(1L, 10L)).thenReturn(false);
        when(userRepo.findById(1L)).thenReturn(Optional.of(USER_1));

        assertThatThrownBy(() -> buildService(matchRepo, predRepo, userRepo)
                .create(1L, 10L, goals, goals, penScore, penScore))
                .isInstanceOf(ValidationException.class);

        verify(predRepo, never()).save(any());
    }

    @Provide
    Arbitrary<MatchStatus> lockedStatuses() {
        return Arbitraries.of(MatchStatus.LIVE, MatchStatus.FINISHED,
                MatchStatus.ADJUSTED, MatchStatus.SUSPENDED, MatchStatus.CANCELLED);
    }

    @Provide
    Arbitrary<MatchStatus> unlockedStatuses() {
        return Arbitraries.of(MatchStatus.SCHEDULED, MatchStatus.POSTPONED);
    }

    @Provide
    Arbitrary<Integer> validGoals() {
        return Arbitraries.integers().between(0, 15);
    }

    @Provide
    Arbitrary<Integer> negativeGoals() {
        return Arbitraries.integers().between(Integer.MIN_VALUE, -1);
    }

    @Provide
    Arbitrary<int[]> differentPenalties() {
        return Arbitraries.integers().between(0, 10)
                .flatMap(a -> Arbitraries.integers().between(0, 10)
                        .filter(b -> !b.equals(a))
                        .map(b -> new int[]{a, b}));
    }
}
