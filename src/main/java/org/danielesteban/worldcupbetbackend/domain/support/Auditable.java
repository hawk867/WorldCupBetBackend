package org.danielesteban.worldcupbetbackend.domain.support;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Mapped superclass that provides automatic {@code createdAt} / {@code updatedAt}
 * timestamp management via Spring Data JPA auditing.
 * <p>
 * Entities that need both timestamps extend this class. The Spring
 * {@link AuditingEntityListener} populates the fields on insert and update,
 * provided {@code @EnableJpaAuditing} is active on a configuration bean.
 * <p>
 * {@code createdAt} is marked {@code updatable = false} so JPA never emits
 * updates for it once the row exists.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
