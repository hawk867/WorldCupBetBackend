package org.danielesteban.worldcupbetbackend.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing so that {@code @CreatedDate} and
 * {@code @LastModifiedDate} fields on entities and mapped superclasses (such
 * as {@link org.danielesteban.worldcupbetbackend.domain.support.Auditable})
 * are populated automatically on insert and update.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
