package com.asms.entity.catalog;

import com.asms.entity.base.BaseEntity;
import com.asms.util.SearchNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * A school of the catalog users pick from (BR-USER-03, UC-USER-12). A school somebody already picked is never deleted,
 * only deactivated: it disappears from the picker but stays on the profiles that have it.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Getter
@Entity
@Table(name = "schools")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class School extends BaseEntity {

    /** Uppercase code, unique */
    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    /** {@link #name} without accents, lowercase: blocks duplicates that only differ by accents and case */
    @Column(name = "name_search", nullable = false, length = 150)
    private String nameSearch;

    @Nullable
    @Column(name = "short_name", length = 30)
    private String shortName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

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

    /** Arguments are already normalized: code uppercase, names trimmed with single spaces. */
    public static School create(String code, String name, @Nullable String shortName, UUID adminId) {
        School school = new School();
        school.createdBy = adminId;
        school.update(code, name, shortName, true, adminId);
        return school;
    }

    public void update(String newCode, String newName, @Nullable String newShortName, boolean isActive, UUID adminId) {
        code = newCode;
        name = newName;
        nameSearch = SearchNormalizer.toSearchKey(newName);
        shortName = newShortName;
        active = isActive;
        updatedBy = adminId;
    }
}
