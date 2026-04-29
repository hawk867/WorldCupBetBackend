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
 * A national team participating in the tournament.
 * <p>
 * {@code externalId} is the identifier from football-data.org and is used as
 * the stable synchronization key between this system and the external API.
 * <p>
 * Master data: teams are {@code ON DELETE RESTRICT} from {@code matches},
 * so a team referenced by any match cannot be deleted.
 */
@Entity
@Table(
        name = "teams",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_teams_external_id",
                columnNames = "external_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false)
    private Integer externalId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 10)
    private String code;

    @Column(name = "flag_url", length = 512)
    private String flagUrl;
}
