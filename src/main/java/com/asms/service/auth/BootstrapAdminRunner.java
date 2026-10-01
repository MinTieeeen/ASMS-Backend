package com.asms.service.auth;

import com.asms.config.AppProperties;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.repository.user.UserRepository;
import com.asms.util.EmailNormalizer;
import com.asms.util.UserCodeNormalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first Admin from {@code APP_BOOTSTRAP_ADMIN_EMAIL}, {@code APP_BOOTSTRAP_ADMIN_USER_CODE} (sign-in code,
 * {@code admin} by default) and {@code APP_BOOTSTRAP_ADMIN_PASSWORD} when no Admin
 * exists yet (FR-AUTH-24, FA-13). The account is active immediately.
 *
 * <p>Does nothing when an Admin already exists or the variables are not set. An invalid configuration is logged and
 * skipped rather than stopping the application. The password variable must be removed after the first run (section
 * 6.2).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordPolicyValidator passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.BootstrapAdmin config = props.bootstrapAdmin();
        if (userRepository.existsBySystemRole(SystemRole.ADMIN)) {
            if (config.isConfigured()) {
                log.warn("An Admin already exists: remove APP_BOOTSTRAP_ADMIN_PASSWORD from the environment");
            }
            return;
        }
        if (!config.isConfigured()) {
            log.warn("No Admin account exists and APP_BOOTSTRAP_ADMIN_EMAIL/PASSWORD are not set");
            return;
        }
        createAdmin(
                EmailNormalizer.normalize(config.email()),
                UserCodeNormalizer.normalize(config.userCode()),
                config.password(),
                config.fullName());
    }

    private void createAdmin(String email, String userCode, String password, String fullName) {
        if (userRepository.existsByEmail(email)) {
            log.error("Bootstrap Admin not created: email {} already belongs to a non-Admin account", email);
            return;
        }
        if (userRepository.existsByUserCode(userCode)) {
            log.error("Bootstrap Admin not created: code {} already belongs to a non-Admin account", userCode);
            return;
        }
        List<PasswordViolation> violations = passwordPolicy.findViolations(password, email);
        if (!violations.isEmpty()) {
            log.error("Bootstrap Admin not created: password violates the policy {}", violations);
            return;
        }
        userRepository.save(User.createBootstrapAdmin(
                email, userCode, fullName, passwordEncoder.encode(password), Instant.now(clock)));
        log.info(
                "Bootstrap Admin {} ({}) created; remove APP_BOOTSTRAP_ADMIN_PASSWORD from the environment",
                userCode,
                email);
    }
}
