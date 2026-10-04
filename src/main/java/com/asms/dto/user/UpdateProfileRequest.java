package com.asms.dto.user;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Body of API-USER-02 (PATCH). Only the fields sent are changed: a field left out keeps its value, a field sent as
 * {@code null} clears it. This class remembers which fields were sent, which a record cannot tell apart.
 *
 * <p>Fields the user may not change (email, user ID, role, status, GitHub) are collected instead of being ignored, so
 * the service can answer {@code VALIDATION_ERROR} with code {@code NOT_ALLOWED}.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public class UpdateProfileRequest {

    @NotNull
    @Min(0)
    @Schema(description = "users.version the client holds (BR-USER-14)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer version;

    @Nullable
    private String fullName;

    @Nullable
    private UUID schoolId;

    @Nullable
    private String bio;

    private boolean fullNameSent;
    private boolean schoolIdSent;
    private boolean bioSent;
    private final Set<String> notAllowedFields = new LinkedHashSet<>();

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Schema(description = "2-100 characters after trimming; cannot be null", nullable = true)
    @Nullable
    public String getFullName() {
        return fullName;
    }

    @JsonSetter("fullName")
    public void setFullName(@Nullable String fullName) {
        this.fullName = fullName;
        this.fullNameSent = true;
    }

    @Schema(description = "Active school from API-USER-21; null removes the school", nullable = true)
    @Nullable
    public UUID getSchoolId() {
        return schoolId;
    }

    @JsonSetter("schoolId")
    public void setSchoolId(@Nullable UUID schoolId) {
        this.schoolId = schoolId;
        this.schoolIdSent = true;
    }

    @Schema(description = "At most 300 characters and 5 lines; null or blank clears it", nullable = true)
    @Nullable
    public String getBio() {
        return bio;
    }

    @JsonSetter("bio")
    public void setBio(@Nullable String bio) {
        this.bio = bio;
        this.bioSent = true;
    }

    @JsonAnySetter
    void collectNotAllowed(String field, @Nullable Object ignored) {
        notAllowedFields.add(field);
    }

    public boolean fullNameSent() {
        return fullNameSent;
    }

    public boolean schoolIdSent() {
        return schoolIdSent;
    }

    public boolean bioSent() {
        return bioSent;
    }

    /** Fields present in the body that this API does not accept */
    public Set<String> notAllowedFields() {
        return Collections.unmodifiableSet(notAllowedFields);
    }
}
