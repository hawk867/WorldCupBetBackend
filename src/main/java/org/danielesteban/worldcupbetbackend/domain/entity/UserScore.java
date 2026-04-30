package org.danielesteban.worldcupbetbackend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Denormalized aggregate of a user's total performance, used by the ranking
 * view.
 * <p>
 * {@code UserScore} shares its primary key with {@link User} through
 * {@link MapsId}: the {@code user_id} column is both the primary key of this
 * table and a foreign key to {@code users(id)}. This guarantees exactly one
 * aggregate row per user and makes user-owned lookups trivial.
 * <p>
 * The schema enforces non-negativity on every counter-column via
 * {@code chk_user_scores_*}. Ranking queries use the composite descending
 * index {@code idx_user_scores_ranking} on
 * {@code (total_points DESC, exact_count DESC)} so the leaderboard can be
 * produced without a sort at query time.
 * <p>
 * {@link #updatedAt} is maintained by Spring Data JPA auditing. The table
 * has no {@code created_at} column because the lifecycle of a score row is
 * tied to the lifecycle of its owning user (insert on user creation, cascade
 * delete with the user).
 */
@Entity
@Table(
        name = "user_scores",
        indexes = @Index(
                name = "idx_user_scores_ranking",
                columnList = "total_points DESC, exact_count DESC"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "userId")
@EntityListeners(AuditingEntityListener.class)
public class UserScore {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            foreignKey = @ForeignKey(name = "fk_user_scores_user")
    )
    private User user;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints;

    @Column(name = "exact_count", nullable = false)
    private Integer exactCount;

    @Column(name = "winner_count", nullable = false)
    private Integer winnerCount;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
