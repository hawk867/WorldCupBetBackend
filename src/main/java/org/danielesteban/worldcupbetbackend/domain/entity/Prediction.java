package org.danielesteban.worldcupbetbackend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * A user's forecast for a specific match.
 * <p>
 * The pair {@code (user, match)} is unique: a user owns at most one
 * prediction per match, enforced by the composite unique constraint
 * {@code uk_predictions_user_match} in {@code V1__baseline_schema.sql}.
 * The match-side FK cascades on delete so removing a match removes its
 * predictions; likewise deleting the user cascades into their predictions.
 * <p>
 * Penalty fields are optional. The database enforces
 * {@code chk_predictions_penalties_pair}, which requires both
 * {@code home_penalties} and {@code away_penalties} to be null together or
 * non-null together, so a prediction cannot contain a partial penalty
 * shootout forecast.
 * <p>
 * Both {@link #createdAt} and {@link #updatedAt} are managed by Spring Data
 * JPA auditing; {@code createdAt} is also marked {@code updatable = false}
 * at the mapping level so it is never overwritten.
 */
@Entity
@Table(
        name = "predictions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_predictions_user_match",
                columnNames = {"user_id", "match_id"}
        ),
        indexes = @Index(
                name = "idx_predictions_match_id",
                columnList = "match_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@EntityListeners(AuditingEntityListener.class)
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_predictions_user")
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "match_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_predictions_match")
    )
    private Match match;

    @Column(name = "home_goals", nullable = false)
    private Integer homeGoals;

    @Column(name = "away_goals", nullable = false)
    private Integer awayGoals;

    @Column(name = "home_penalties")
    private Integer homePenalties;

    @Column(name = "away_penalties")
    private Integer awayPenalties;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
