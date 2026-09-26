package com.asms.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asms.config.AppProperties;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.repository.user.UserRepository;
import com.asms.support.TestProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("FR-AUTH-24: first Admin from environment variables")
class BootstrapAdminRunnerTest {

    private static final Instant NOW = Instant.parse("2026-09-26T01:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void run_shouldCreateActiveAdmin_whenNoneExists() {
        when(passwordEncoder.encode("Quantri2026")).thenReturn("hash");

        runner(" Admin@ASMS.local ", "Quantri2026").run(new DefaultApplicationArguments());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@asms.local");
        assertThat(saved.getValue().getSystemRole()).isEqualTo(SystemRole.ADMIN);
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    void run_shouldDoNothing_whenAdminExists() {
        when(userRepository.existsBySystemRole(SystemRole.ADMIN)).thenReturn(true);

        runner("admin@asms.local", "Quantri2026").run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_shouldSkip_whenPasswordViolatesPolicy() {
        runner("admin@asms.local", "weak").run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_shouldSkip_whenNotConfigured() {
        runner(null, null).run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
    }

    private BootstrapAdminRunner runner(String email, String password) {
        AppProperties defaults = TestProperties.appProperties();
        AppProperties props = new AppProperties(
                defaults.frontendUrl(),
                defaults.defaultTimezone(),
                defaults.jwt(),
                defaults.cors(),
                defaults.auth(),
                new AppProperties.BootstrapAdmin(email, password, "Quản trị viên"),
                defaults.mail());
        return new BootstrapAdminRunner(
                userRepository,
                new PasswordPolicyValidator(),
                passwordEncoder,
                props,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
