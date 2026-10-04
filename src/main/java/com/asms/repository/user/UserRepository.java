package com.asms.repository.user;

import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link User}. Emails are always passed already normalized (lowercase, trimmed; BR-AUTH-02).
 *
 * @author MinhTien
 * @version 1.1.0
 * @since 2026-09-26
 * @modified 2026-10-04
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUserCode(String userCode);

    /**
     * Login lookup with a row lock: the failed-login counter is not covered by {@code @Version}, so concurrent wrong
     * passwords for one account are serialized here instead (BR-AUTH-04).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userCode = :userCode")
    Optional<User> findByUserCodeForUpdate(@Param("userCode") String userCode);

    boolean existsByEmail(String email);

    boolean existsByUserCode(String userCode);

    boolean existsBySystemRole(SystemRole systemRole);

    /** Row lock for Admin actions on one user (section 8.5); waits for a concurrent action on the same user. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Locks every active Admin row, then returns them. Two Admins demoting or locking the last two Admins at the same
     * time queue on these locks, so only one succeeds (BR-USER-10, NFR-USER-11).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.systemRole = com.asms.entity.user.SystemRole.ADMIN"
            + " and u.status = com.asms.entity.user.UserStatus.ACTIVE")
    List<User> lockActiveAdmins();

    boolean existsBySchool_Id(UUID schoolId);

    /** Every avatar key in use, for the orphan cleanup (NFR-USER-14) */
    @Query("select u.avatarKey from User u where u.avatarKey is not null")
    Set<String> findAllAvatarKeys();
}
