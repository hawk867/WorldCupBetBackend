package org.danielesteban.worldcupbetbackend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * A scheduled or played fixture between two teams.
 * <p>
 * Each match references a {@link Stage}, a home {@link Team}, and an away
 * {@link Team} via lazy many-to-one associations. The {@code externalId}
 * comes from football-data.org and keeps match rows aligned with the
 * external API during scheduled sync cycles.
 * <p>
 * {@link #status} uses {@link MatchStatus} persisted as a string and is
 * further constrained at the database level by {@code chk_matches_status}.
 * The database also enforces:
 * <ul>
 *   <li>{@code chk_matches_distinct_teams}: home and away teams must differ.</li>
 *   <li>{@code chk_matches_*_goals} / {@code chk_matches_*_penalties}: all
 *       score-related counters are non-negative when present (null is allowed
 *       before the match is played).</li>
 *   <li>{@code chk_matches_penalties_consistency}: if
 *       {@link #wentToPenalties} is true, both penalty counts must be
 *       non-null.</li>
 * </ul>
 * <p>
 * Score fields ({@code homeGoals}, {@code awayGoals}, {@code homePenalties},
 * {@code awayPenalties}) are {@link Integer} rather than primitives so they
 * can carry {@code null} before a result exists.
 * <p>
 * The {@code updated_at} column is maintained by Spring Data JPA auditing
 * (no {@code created_at} column on this table). The entity does NOT extend
 * {@link org.danielesteban.worldcupbetbackend.domain.support.Auditable},
 * which carries both timestamps.
 */
@Entity
@Table(
        name = "matches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_matches_external_id",
                columnNames = "external_id"
        ),
        indexes = {
                @Index(name = "idx_matches_status",     columnList = "status"),
                @Index(name = "idx_matches_kickoff_at", columnList = "kickoff_at"),
                @Index(name = "idx_matches_stage_id",   columnList = "stage_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@EntityListeners(AuditingEntityListener.class)
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false)
    private Integer externalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "stage_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_matches_stage")
    )
    private Stage stage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "home_team_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_matches_home_team")
    )
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "away_team_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_matches_away_team")
    )
    private Team awayTeam;

    @Column(name = "kickoff_at", nullable = false)
    private Instant kickoffAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MatchStatus status;

    @Column(name = "home_goals")
    private Integer homeGoals;

    @Column(name = "away_goals")
    private Integer awayGoals;

    @Column(name = "home_penalties")
    private Integer homePenalties;

    @Column(name = "away_penalties")
    private Integer awayPenalties;

    @Column(name = "went_to_penalties", nullable = false)
    private boolean wentToPenalties;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
