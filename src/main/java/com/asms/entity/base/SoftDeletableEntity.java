package com.asms.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Superclass of soft-deletable entities (BR08).
 *
 * <p>Subclasses must declare {@code @SQLRestriction("deleted_at IS NULL")} so that default queries skip deleted rows.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Getter
@Setter
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted(Instant now) {
        this.deletedAt = now;
    }

    public void restore() {
        this.deletedAt = null;
    }
}
