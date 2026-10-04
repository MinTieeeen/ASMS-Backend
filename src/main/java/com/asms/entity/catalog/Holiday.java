package com.asms.entity.catalog;

import com.asms.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * A system-wide holiday period, both dates included (UC-USER-13). Periods never overlap: the database enforces it with
 * {@code ex_holidays_no_overlap} (BR-USER-21).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Getter
@Entity
@Table(name = "holidays")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HolidayKind kind;

    /** Same calendar date every year, so it can be copied to the next year */
    @Column(name = "repeats_yearly", nullable = false)
    private boolean repeatsYearly;

    @Nullable
    @Column(length = 300)
    private String note;

    /** NULL only for the rows seeded by migration V11 */
    @Nullable
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Nullable
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private int version;

    public static Holiday create(
            String name,
            LocalDate startDate,
            LocalDate endDate,
            HolidayKind kind,
            boolean repeatsYearly,
            @Nullable String note,
            UUID adminId) {
        Holiday holiday = new Holiday();
        holiday.createdBy = adminId;
        holiday.update(name, startDate, endDate, kind, repeatsYearly, note, adminId);
        return holiday;
    }

    public void update(
            String newName,
            LocalDate newStartDate,
            LocalDate newEndDate,
            HolidayKind newKind,
            boolean newRepeatsYearly,
            @Nullable String newNote,
            UUID adminId) {
        name = newName;
        startDate = newStartDate;
        endDate = newEndDate;
        kind = newKind;
        repeatsYearly = newRepeatsYearly;
        note = newNote;
        updatedBy = adminId;
    }

    /** Number of days off, both ends included */
    public int days() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
