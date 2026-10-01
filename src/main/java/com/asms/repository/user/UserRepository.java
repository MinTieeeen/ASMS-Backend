package com.asms.repository.user;

import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Data access for {@link User}. Emails are always passed already normalized (lowercase, trimmed; BR-AUTH-02).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUserCode(String userCode);

    boolean existsByEmail(String email);

    boolean existsByUserCode(String userCode);

    boolean existsBySystemRole(SystemRole systemRole);
}
