package com.asms.service.auth;

import com.asms.dto.admin.ActivationEmailResponse;
import com.asms.dto.admin.AdminUserFilter;
import com.asms.dto.admin.AdminUserResponse;
import com.asms.dto.admin.CreateUserRequest;
import com.asms.dto.common.ClientInfo;
import com.asms.dto.common.PageResponse;
import com.asms.entity.auth.AuthEventType;
import com.asms.entity.auth.UserTokenType;
import com.asms.entity.user.Language;
import com.asms.entity.user.User;
import com.asms.event.auth.AuthEventOccurred;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.exception.GlobalExceptionHandler.FieldError;
import com.asms.exception.ProblemDetailFactory;
import com.asms.mapper.user.UserMapper;
import com.asms.repository.user.UserRepository;
import com.asms.repository.user.UserSpecifications;
import com.asms.security.RateLimitPolicy;
import com.asms.security.RateLimitService;
import com.asms.util.EmailNormalizer;
import com.asms.util.UserCodeNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account management by an Admin (UC-AUTH-07, UC-AUTH-08): create an account that the user activates by email, resend
 * the activation email, list users. Only Admins may call it (BR-AUTH-15); the URL rule in {@code SecurityConfig} is
 * repeated here as a second guard.
 *
 * <p>The user list is a minimal version for SCR-AUTH-06; the full user management belongs to the admin module (M12).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserService {

    private static final int FULL_NAME_MIN_LENGTH = 2;
    private static final int FULL_NAME_MAX_LENGTH = 100;
    private static final Pattern USER_CODE = Pattern.compile("^[A-Za-z0-9]{1,20}$");
    private static final String EMAIL_CONSTRAINT = "uq_users_email";
    private static final String USER_CODE_CONSTRAINT = "uq_users_user_code";

    private final UserRepository userRepository;
    private final UserTokenService userTokenService;
    private final AuthMailService mailService;
    private final RateLimitService rateLimitService;
    private final AuthEventPublisher events;
    private final UserMapper userMapper;

    /**
     * Creates a pending account and queues the welcome email with a 72-hour activation link (UC-AUTH-07). The account
     * is created even if the email later fails; the Admin can resend it (FR-AUTH-21).
     *
     * @throws BusinessException {@code VALIDATION_ERROR}, {@code USER_EMAIL_EXISTS} or
     *     {@code USER_CODE_EXISTS}
     */
    @Transactional
    public AdminUserResponse createUser(UUID adminId, CreateUserRequest request, ClientInfo client) {
        String email = EmailNormalizer.normalize(request.email());
        String fullName = request.fullName().strip();
        String userCode = UserCodeNormalizer.normalize(request.userCode());
        validate(fullName, userCode);
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.USER_EMAIL_EXISTS);
        }
        if (userRepository.existsByUserCode(userCode)) {
            throw new BusinessException(ErrorCode.USER_CODE_EXISTS);
        }

        User user = User.createPending(email, fullName, userCode, request.systemRole(), adminId);
        user.changeLanguage(request.language() == null ? Language.DEFAULT : request.language());
        saveNew(user);
        UserTokenService.IssuedToken token =
                userTokenService.issue(user, UserTokenType.ACTIVATION, adminId, client.ipAddress());
        mailService.sendActivation(user, token.rawToken(), token.expiresAt());
        // TODO(F12.07): also write admin_audit_logs once the admin module (M12) exists
        events.publish(AuthEventOccurred.of(AuthEventType.USER_CREATED, client)
                .withUser(user.getId())
                .withActor(adminId)
                .withEmail(email)
                .withMetadata("systemRole", request.systemRole()));
        return userMapper.toAdminResponse(user);
    }

    /**
     * Issues a new activation link, invalidating the previous one, and queues the email again (UC-AUTH-08,
     * FR-AUTH-22). At most 5 times per hour for the same account.
     *
     * @throws BusinessException {@code USER_NOT_FOUND}, {@code USER_NOT_PENDING} or {@code AUTH_RATE_LIMITED}
     */
    @Transactional
    public ActivationEmailResponse resendActivation(UUID adminId, UUID userId, ClientInfo client) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!user.isPendingActivation()) {
            throw new BusinessException(ErrorCode.USER_NOT_PENDING);
        }
        rateLimitService.check(RateLimitPolicy.ACTIVATION_RESEND_USER, userId.toString());

        UserTokenService.IssuedToken token =
                userTokenService.issue(user, UserTokenType.ACTIVATION, adminId, client.ipAddress());
        mailService.sendActivation(user, token.rawToken(), token.expiresAt());
        events.publish(AuthEventOccurred.of(AuthEventType.ACTIVATION_RESENT, client)
                .withUser(userId)
                .withActor(adminId)
                .withEmail(user.getEmail()));
        return new ActivationEmailResponse(token.expiresAt());
    }

    /** Users matching the filter, newest first by default (SCR-AUTH-06). */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(AdminUserFilter filter, Pageable pageable) {
        Specification<User> spec = Specification.allOf(
                UserSpecifications.hasStatus(filter.status()), UserSpecifications.matchesKeyword(filter.keyword()));
        return PageResponse.from(userRepository.findAll(spec, pageable), userMapper::toAdminResponse);
    }

    /** Saves and flushes so that a concurrent duplicate is reported as a business error, not a 500. */
    private void saveNew(User user) {
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            String message = String.valueOf(e.getMostSpecificCause().getMessage());
            if (message.contains(USER_CODE_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.USER_CODE_EXISTS);
            }
            if (message.contains(EMAIL_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.USER_EMAIL_EXISTS);
            }
            throw e;
        }
    }

    /** BR-AUTH-14 on the trimmed values; field codes match Bean Validation errors. */
    private static void validate(String fullName, String userCode) {
        List<FieldError> errors = new ArrayList<>();
        int nameLength = fullName.codePointCount(0, fullName.length());
        if (nameLength < FULL_NAME_MIN_LENGTH || nameLength > FULL_NAME_MAX_LENGTH) {
            errors.add(new FieldError("fullName", "INVALID_LENGTH", null));
        }
        if (!USER_CODE.matcher(userCode).matches()) {
            errors.add(new FieldError("userCode", "INVALID_FORMAT", null));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, Map.of(ProblemDetailFactory.ERRORS, errors));
        }
    }
}
