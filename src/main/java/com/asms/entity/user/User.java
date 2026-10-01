package com.asms.entity.user;

import com.asms.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * User account. The Auth module owns the authentication columns; profile columns are added by other modules later.
 *
 * <p>State changes go through domain methods so that account invariants (BR-AUTH-03, BR-AUTH-04) stay in one place.
 * {@code @Version} protects the failed-login counter against concurrent updates.
 *
 * @author MinhTien
 * @version 1.1.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String email;

    @Nullable
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /** User ID, the sign-in identifier: the MSSV of a student, a code such as ADMIN for an Admin; stored uppercase */
    @Column(name = "user_code", nullable = false, length = 20)
    private String userCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false, length = 20)
    private SystemRole systemRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "failed_login_count", nullable = false)
    private short failedLoginCount;

    @Nullable
    @Column(name = "last_failed_login_at")
    private Instant lastFailedLoginAt;

    @Nullable
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Nullable
    @Column(name = "locked_at")
    private Instant lockedAt;

    @Nullable
    @Column(name = "locked_reason", length = 500)
    private String lockedReason;

    @Nullable
    @Column(name = "activated_at")
    private Instant activatedAt;

    @Nullable
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Nullable
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Nullable
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Nullable
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Language language = Language.DEFAULT;

    @Nullable
    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(nullable = false)
    private int version;

    /** Account created by an Admin: no password until the user activates it (UC-AUTH-07). */
    public static User createPending(
            String email, String fullName, String userCode, SystemRole systemRole, @Nullable UUID createdBy) {
        User user = new User();
        user.email = email;
        user.fullName = fullName;
        user.userCode = userCode;
        user.systemRole = systemRole;
        user.status = UserStatus.PENDING_ACTIVATION;
        user.createdBy = createdBy;
        return user;
    }

    /** First Admin created from environment variables, active immediately (FR-AUTH-24). */
    public static User createBootstrapAdmin(
            String email, String userCode, String fullName, String passwordHash, Instant now) {
        User user = createPending(email, fullName, userCode, SystemRole.ADMIN, null);
        user.activate(passwordHash, now);
        return user;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isPendingActivation() {
        return status == UserStatus.PENDING_ACTIVATION;
    }

    public boolean isAdmin() {
        return systemRole == SystemRole.ADMIN;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isTemporarilyLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Counts one more consecutive wrong password (BR-AUTH-04). A temporary lock that has already expired resets the
     * counter first.
     *
     * @return the number of consecutive failures including this one
     */
    public int recordFailedLogin(Instant now) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            clearTemporaryLock();
        }
        failedLoginCount++;
        lastFailedLoginAt = now;
        return failedLoginCount;
    }

    public void lockTemporarily(Instant until) {
        lockedUntil = until;
    }

    public void recordSuccessfulLogin(Instant now) {
        clearTemporaryLock();
        lastLoginAt = now;
    }

    /** Sets the first password and makes the account usable (UC-AUTH-05). */
    public void activate(String newPasswordHash, Instant now) {
        passwordHash = newPasswordHash;
        status = UserStatus.ACTIVE;
        activatedAt = now;
        emailVerifiedAt = now;
        passwordChangedAt = now;
    }

    /** Stores a new password after a change or a reset; both end any temporary lock (BR-AUTH-04). */
    public void changePassword(String newPasswordHash, Instant now) {
        passwordHash = newPasswordHash;
        passwordChangedAt = now;
        clearTemporaryLock();
    }

    public void changeLanguage(Language newLanguage) {
        language = newLanguage;
    }

    private void clearTemporaryLock() {
        failedLoginCount = 0;
        lockedUntil = null;
    }
}
