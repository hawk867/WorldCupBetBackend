package org.danielesteban.worldcupbetbackend.persistence.repository;

import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Match}.
 */
public interface MatchRepository extends JpaRepository<Match, Long> {

    /**
     * Finds the match whose football-data.org identifier equals {@code externalId}.
     * Used by the sync scheduler to reconcile live data.
     */
    Optional<Match> findByExternalId(Integer externalId);

    /**
     * Returns every match in the given status. Backed by {@code idx_matches_status}.
     */
    List<Match> findAllByStatus(MatchStatus status);

    /**
     * Returns every match whose status is one of the given values. Used by
     * scoring and scheduler sweeps that need more than one state at once
     * (e.g. {@code LIVE} + {@code FINISHED}).
     */
    List<Match> findAllByStatusIn(Collection<MatchStatus> statuses);

    /**
     * Returns matches whose kickoff has already passed but whose status has
     * not yet transitioned. Used by the scheduler to move rows from
     * {@code SCHEDULED} to {@code LIVE} when the kickoff time is reached.
     */
    List<Match> findAllByKickoffAtBeforeAndStatus(Instant cutoff, MatchStatus status);

    /**
     * Returns every match of a given stage, ordered by kickoff. Backs the
     * per-stage calendar view in the UI.
     */
    List<Match> findAllByStageIdOrderByKickoffAtAsc(Long stageId);
}
