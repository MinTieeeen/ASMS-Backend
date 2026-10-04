package com.asms.service.user;

import com.asms.dto.user.PublicProfileResponse;
import com.asms.dto.user.UpdateProfileRequest;
import com.asms.dto.user.UserProfileResponse;
import com.asms.entity.catalog.School;
import com.asms.entity.user.User;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.exception.GlobalExceptionHandler.FieldError;
import com.asms.exception.ProblemDetailFactory;
import com.asms.mapper.user.ProfileMapper;
import com.asms.repository.catalog.SchoolRepository;
import com.asms.repository.user.UserGithubAccountRepository;
import com.asms.repository.user.UserRepository;
import com.asms.security.RateLimitPolicy;
import com.asms.security.RateLimitService;
import com.asms.util.SearchNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profile of the signed-in user and the public profile card of others (UC-USER-01, UC-USER-03; API-USER-01, 02, 05).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    static final int FULL_NAME_MIN_LENGTH = 2;
    static final int FULL_NAME_MAX_LENGTH = 100;
    static final int BIO_MAX_LENGTH = 300;
    static final int BIO_MAX_LINES = 5;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cc}");
    private static final Pattern LINE_BREAKS = Pattern.compile("\\r\\n?");

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final UserGithubAccountRepository githubRepository;
    private final RateLimitService rateLimitService;
    private final ProfileMapper profileMapper;

    /** API-USER-01 */
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return profileMapper.toProfile(user, githubRepository.findById(userId).orElse(null));
    }

    /**
     * Changes only the fields sent (API-USER-02). The request must carry the version the client read; an older one
     * means the profile changed meanwhile, in another tab or by an Admin (BR-USER-14).
     *
     * @throws BusinessException {@code AUTH_RATE_LIMITED}, {@code VALIDATION_ERROR} or {@code USER_VERSION_CONFLICT}
     */
    @Transactional
    public UserProfileResponse updateMyProfile(UUID userId, UpdateProfileRequest request) {
        rateLimitService.check(RateLimitPolicy.PROFILE_UPDATE_USER, userId.toString());
        ProfileChanges changes = validate(request);

        User user = userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getVersion() != request.getVersion()) {
            throw new BusinessException(ErrorCode.USER_VERSION_CONFLICT);
        }
        if (changes.fullName() != null) {
            user.changeFullName(changes.fullName());
        }
        if (request.schoolIdSent() && !sameSchool(user.getSchool(), request.getSchoolId())) {
            user.changeSchool(findSelectableSchool(request.getSchoolId()));
        }
        if (request.bioSent()) {
            user.changeBio(changes.bio());
        }
        userRepository.saveAndFlush(user);
        return profileMapper.toProfile(user, githubRepository.findById(userId).orElse(null));
    }

    /**
     * Public profile card (API-USER-05). Accounts that are not active answer like unknown ones, so the card never
     * reveals a status (BR-USER-18).
     */
    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(UUID userId) {
        User user = userRepository
                .findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return profileMapper.toPublicProfile(
                user, githubRepository.findById(userId).orElse(null));
    }

    /** Normalizes (FR-USER-03) and checks every sent field, reporting all errors at once. */
    private ProfileChanges validate(UpdateProfileRequest request) {
        List<FieldError> errors = new ArrayList<>();
        request.notAllowedFields().forEach(field -> errors.add(new FieldError(field, "NOT_ALLOWED", null)));

        String fullName = null;
        if (request.fullNameSent()) {
            fullName = SearchNormalizer.collapseWhitespace(request.getFullName());
            fullNameError(fullName).ifPresent(code -> errors.add(new FieldError("fullName", code, null)));
        }
        String bio = null;
        if (request.bioSent() && request.getBio() != null) {
            bio = normalizeBio(request.getBio());
            if (bio != null && (length(bio) > BIO_MAX_LENGTH || bio.split("\n", -1).length > BIO_MAX_LINES)) {
                errors.add(new FieldError("bio", "TOO_LONG", null));
            }
        }
        if (!errors.isEmpty()) {
            throw validationError(errors);
        }
        return new ProfileChanges(fullName, bio);
    }

    /** BR-USER-01; shared with the Admin edit of API-USER-09 */
    static Optional<String> fullNameError(@Nullable String normalizedFullName) {
        if (normalizedFullName == null) {
            return Optional.of("REQUIRED");
        }
        int length = length(normalizedFullName);
        if (length < FULL_NAME_MIN_LENGTH) {
            return Optional.of("TOO_SHORT");
        }
        if (length > FULL_NAME_MAX_LENGTH) {
            return Optional.of("TOO_LONG");
        }
        if (CONTROL_CHARACTERS.matcher(normalizedFullName).find()) {
            return Optional.of("INVALID_FORMAT");
        }
        return Optional.empty();
    }

    /** Only an active school can be picked (BR-USER-03); {@code null} removes the school. */
    @Nullable
    private School findSelectableSchool(@Nullable UUID schoolId) {
        if (schoolId == null) {
            return null;
        }
        return schoolRepository
                .findById(schoolId)
                .filter(School::isActive)
                .orElseThrow(() -> validationError(List.of(new FieldError("schoolId", "NOT_AVAILABLE", null))));
    }

    /** Sending the current school again is not a change, even when it was deactivated since. */
    private static boolean sameSchool(@Nullable School current, @Nullable UUID schoolId) {
        return Objects.equals(current == null ? null : current.getId(), schoolId);
    }

    /** Plain text: trimmed, Windows line breaks unified, blank becomes {@code null} (BR-USER-06). */
    @Nullable
    private static String normalizeBio(String bio) {
        String unified = LINE_BREAKS.matcher(bio).replaceAll("\n").strip();
        return unified.isEmpty() ? null : unified;
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    private static BusinessException validationError(List<FieldError> errors) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, Map.of(ProblemDetailFactory.ERRORS, errors));
    }

    private record ProfileChanges(
            @Nullable String fullName, @Nullable String bio) {}
}
