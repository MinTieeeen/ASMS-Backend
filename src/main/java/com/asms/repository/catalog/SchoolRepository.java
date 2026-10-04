package com.asms.repository.catalog;

import com.asms.entity.catalog.School;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Data access for {@link School}.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public interface SchoolRepository extends JpaRepository<School, UUID>, JpaSpecificationExecutor<School> {

    /** Picker of SCR-USER-01 and SCR-USER-05 (API-USER-21) */
    List<School> findByActiveTrueOrderByNameAsc();

    boolean existsByCode(String code);

    boolean existsByNameSearch(String nameSearch);
}
