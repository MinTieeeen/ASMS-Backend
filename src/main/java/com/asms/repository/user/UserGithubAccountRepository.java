package com.asms.repository.user;

import com.asms.entity.user.UserGithubAccount;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link UserGithubAccount}; the id is the ASMS user id.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public interface UserGithubAccountRepository extends JpaRepository<UserGithubAccount, UUID> {

    boolean existsByGithubId(long githubId);
}
