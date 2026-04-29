package org.danielesteban.worldcupbetbackend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tournament phase (e.g., Group Stage, Round of 16, Final).
 * <p>
 * {@code orderIdx} provides the chronological ordering used by UI listings
 * and by {@code StageRepository.findAllByOrderByOrderIdxAsc()}. Seeded at
 * migration time by {@code V2__reference_data_stages.sql}.
 */
@Entity
@Table(
        name = "stages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stages_name",
                columnNames = "name"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Stage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "order_idx", nullable = false)
    private Integer orderIdx;
}
