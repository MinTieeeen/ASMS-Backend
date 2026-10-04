package com.asms.repository.catalog;

import com.asms.entity.catalog.Holiday;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link Holiday}. The year of a holiday is the year of its start date.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    List<Holiday> findByStartDateBetweenOrderByStartDateAsc(LocalDate from, LocalDate to);
}
