package com.asms.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asms.dto.admin.ActivationEmailResponse;
import com.asms.dto.admin.AdminUserResponse;
import com.asms.dto.admin.CreateUserRequest;
import com.asms.dto.common.ClientInfo;
import com.asms.entity.auth.UserTokenType;
import com.asms.entity.user.Language;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.entity.user.UserStatus;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.exception.ProblemDetailFactory;
import com.asms.mapper.user.UserMapperImpl;
import com.asms.repository.user.UserRepository;
import com.asms.security.RateLimitPolicy;
import com.asms.security.RateLimitService;
import com.asms.support.TestUserCodes;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class AdminUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T01:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.1", "UA");
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserTokenService userTokenService = mock(UserTokenService.class);
    private final AuthMailService mailService = mock(AuthMailService.class);
    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final AuthEventPublisher events = mock(AuthEventPublisher.class);

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
                userRepository, userTokenService, mailService, rateLimitService, events, new UserMapperImpl());
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(UUID.randomUUID());
            user.setCreatedAt(NOW);
            return user;
        });
        when(userTokenService.issue(any(), eq(UserTokenType.ACTIVATION), eq(ADMIN_ID), anyString()))
                .thenReturn(new UserTokenService.IssuedToken("raw", NOW.plusSeconds(72 * 3600)));
    }

    @Test
    @DisplayName("UC-AUTH-07: the account is pending, has no password and gets the welcome email")
    void createUser_shouldCreatePendingAccountAndSendActivation() {
        AdminUserResponse created = service.createUser(
                ADMIN_ID,
                new CreateUserRequest(" New.User@Gmail.com ", "  Tran Thi B  ", " se21120001 ", SystemRole.USER, null),
                CLIENT);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.getEmail()).isEqualTo("new.user@gmail.com");
        assertThat(user.getFullName()).isEqualTo("Tran Thi B");
        assertThat(user.getUserCode()).isEqualTo("SE21120001");
        assertThat(user.hasPassword()).isFalse();
        assertThat(user.getCreatedBy()).isEqualTo(ADMIN_ID);
        assertThat(user.getLanguage()).isEqualTo(Language.VI);
        assertThat(created.status()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        verify(mailService).sendActivation(user, "raw", NOW.plusSeconds(72 * 3600));
    }

    @Test
    @DisplayName("Admins get a User ID too, e.g. admin1, stored uppercase")
    void createUser_shouldKeepChosenLanguageAndAdminCode() {
        service.createUser(
                ADMIN_ID,
                new CreateUserRequest("a@gmail.com", "Nguyen Van A", "admin1", SystemRole.ADMIN, Language.EN),
                CLIENT);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getLanguage()).isEqualTo(Language.EN);
        assertThat(saved.getValue().getUserCode()).isEqualTo("ADMIN1");
        assertThat(saved.getValue().getSystemRole()).isEqualTo(SystemRole.ADMIN);
    }

    @Test
    @DisplayName("FR-AUTH-20: email must be unique")
    void createUser_shouldReject_whenEmailExists() {
        when(userRepository.existsByEmail("a@gmail.com")).thenReturn(true);

        assertCode(
                () -> service.createUser(ADMIN_ID, request("A@gmail.com", "Nguyen Van A", "SE1"), CLIENT),
                ErrorCode.USER_EMAIL_EXISTS);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("FR-AUTH-20: user ID must be unique, whatever its case")
    void createUser_shouldReject_whenUserCodeExists() {
        when(userRepository.existsByUserCode("SE21120001")).thenReturn(true);

        assertCode(
                () -> service.createUser(ADMIN_ID, request("a@gmail.com", "Nguyen Van A", "se21120001"), CLIENT),
                ErrorCode.USER_CODE_EXISTS);
    }

    @Test
    @DisplayName("BR-AUTH-14: trimmed full name of 2-100 characters, user ID letters and digits only")
    void createUser_shouldReportFieldErrors_whenDataInvalid() {
        assertThatThrownBy(() -> service.createUser(ADMIN_ID, request("a@gmail.com", " A ", "21-120"), CLIENT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat((List<?>) e.getProperties().get(ProblemDetailFactory.ERRORS))
                            .hasSize(2);
                });
    }

    @Test
    void createUser_shouldMapConcurrentDuplicate_toBusinessError() {
        doThrow(new DataIntegrityViolationException("duplicate key value violates \"uq_users_email\""))
                .when(userRepository)
                .saveAndFlush(any());

        assertCode(
                () -> service.createUser(ADMIN_ID, request("a@gmail.com", "Nguyen Van A", "SE1"), CLIENT),
                ErrorCode.USER_EMAIL_EXISTS);
    }

    @Test
    @DisplayName("UC-AUTH-08: resend issues a new link for a pending account")
    void resendActivation_shouldIssueNewLink() {
        User pending = pendingUser();

        ActivationEmailResponse response = service.resendActivation(ADMIN_ID, pending.getId(), CLIENT);

        assertThat(response.activationExpiresAt()).isEqualTo(NOW.plusSeconds(72 * 3600));
        verify(rateLimitService)
                .check(RateLimitPolicy.ACTIVATION_RESEND_USER, pending.getId().toString());
        verify(mailService).sendActivation(pending, "raw", NOW.plusSeconds(72 * 3600));
    }

    @Test
    @DisplayName("UC-AUTH-08 2a: only pending accounts can get the email again")
    void resendActivation_shouldReject_whenAccountActive() {
        User active = pendingUser();
        active.activate("hash", NOW);

        assertCode(() -> service.resendActivation(ADMIN_ID, active.getId(), CLIENT), ErrorCode.USER_NOT_PENDING);
    }

    @Test
    void resendActivation_shouldReject_whenUserUnknown() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        assertCode(() -> service.resendActivation(ADMIN_ID, unknown, CLIENT), ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("UC-AUTH-08 2b: more than 5 resends per hour are rejected")
    void resendActivation_shouldReject_whenRateLimited() {
        User pending = pendingUser();
        doThrow(BusinessException.retryAfter(ErrorCode.AUTH_RATE_LIMITED, 600))
                .when(rateLimitService)
                .check(RateLimitPolicy.ACTIVATION_RESEND_USER, pending.getId().toString());

        assertCode(() -> service.resendActivation(ADMIN_ID, pending.getId(), CLIENT), ErrorCode.AUTH_RATE_LIMITED);
        verify(mailService, never()).sendActivation(any(), anyString(), any());
    }

    private User pendingUser() {
        User user = User.createPending(
                "p@gmail.com", "Pending User", TestUserCodes.codeFor("p@gmail.com"), SystemRole.USER, ADMIN_ID);
        user.setId(UUID.randomUUID());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    private static CreateUserRequest request(String email, String fullName, String userCode) {
        return new CreateUserRequest(email, fullName, userCode, SystemRole.USER, null);
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(code);
    }
}
