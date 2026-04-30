package org.danielesteban.worldcupbetbackend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The computed points awarded for a single {@link Prediction}.
 * <p>
 * Zero or one row per prediction, enforced by the unique constraint
 * {@code uk_prediction_scores_prediction} on {@code prediction_id}. The
 * scoring service writes one of these rows each time a match reaches a
 * score-bearing status ({@link org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus#FINISHED}
 * or {@link org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus#ADJUSTED}).
 * <p>
 * The database enforces every component of the scoring contract declared in
 * the design:
 * <ul>
 *   <li>All point columns are non-negative
 *       ({@code chk_prediction_scores_*_points}).</li>
 *   <li>{@code points == exact_score_points + winner_points + penalties_points}
 *       ({@code chk_prediction_scores_sum}), so the decomposition always
 *       matches the headline total.</li>
 * </ul>
 * <p>
 * {@link #calculatedAt} is populated by the scoring service, not by Spring
 * Data JPA auditing; this entity does not carry the {@code @CreatedDate}
 * annotation because the timestamp has a domain meaning (when the score was
 * computed) distinct from "when the row was inserted".
 */
@Entity
@Table(
        name = "prediction_scores",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_prediction_scores_prediction",
                columnNames = "prediction_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PredictionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "prediction_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_prediction_scores_prediction")
    )
    private Prediction prediction;

    @Column(nullable = false)
    private Integer points;

    @Column(name = "exact_score_points", nullable = false)
    private Integer exactScorePoints;

    @Column(name = "winner_points", nullable = false)
    private Integer winnerPoints;

    @Column(name = "penalties_points", nullable = false)
    private Integer penaltiesPoints;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;
}
