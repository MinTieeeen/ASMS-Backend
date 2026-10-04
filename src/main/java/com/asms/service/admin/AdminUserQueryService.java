package com.asms.service.admin;

import com.asms.dto.admin.AdminUserDetailResponse;
import com.asms.dto.admin.AdminUserListParams;
import com.asms.dto.admin.AdminUserSummaryResponse;
import com.asms.dto.admin.AuthEventItemResponse;
import com.asms.dto.admin.UserRefResponse;
import com.asms.dto.common.CursorResponse;
import com.asms.dto.common.PageResponse;
import com.asms.entity.auth.AuthEvent;
import com.asms.entity.user.User;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.exception.GlobalExceptionHandler.FieldError;
import com.asms.exception.ProblemDetailFactory;
import com.asms.mapper.user.ProfileMapper;
import com.asms.repository.auth.AuthEventRepository;
import com.asms.repository.auth.UserSessionRepository;
import com.asms.repository.user.UserGithubAccountRepository;
import com.asms.repository.user.UserRepository;
import com.asms.repository.user.UserSpecifications;
import com.asms.service.auth.DeviceLabelParser;
import com.asms.service.user.AvatarUrlResolver;
import com.asms.util.CursorCodec;
import com.asms.util.CursorCodec.Position;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Read side of the Admin user management (UC-USER-04, UC-USER-05; API-USER-07, 08, 14). Only Admins may call it
 * (NFR-USER-04); the URL rule in {@code SecurityConfig} is repeated here as a second guard.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserQueryService {

    private static final String DEFAULT_SORT = "createdAt";
    private static final int DEFAULT_EVENT_LIMIT = 20;
    private static final int MAX_EVENT_LIMIT = 50;
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final UserGithubAccountRepository githubRepository;
    private final AuthEventRepository authEventRepository;
    private final ProfileMapper profileMapper;
    private final AvatarUrlResolver avatarUrls;
    private final DeviceLabelParser deviceLabels;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** API-USER-07: newest first by default, 20 per page, {@code id} as tie-breaker so pages never overlap */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> listUsers(AdminUserListParams params) {
        Specification<User> spec = Specification.allOf(
                UserSpecifications.matchesSearch(params.q()),
                UserSpecifications.hasStatusIn(params.status()),
                UserSpecifications.hasRole(params.role()),
                UserSpecifications.hasSchool(params.schoolId()));
        int page = Objects.requireNonNullElse(params.page(), 1) - 1;
        int size = Objects.requireNonNullElse(params.size(), AdminUserListParams.DEFAULT_SIZE);
        PageRequest request = PageRequest.of(page, size, toSort(params.sort()));
        return PageResponse.from(userRepository.findAll(spec, request), this::toSummary);
    }

    /** API-USER-08 */
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUser(UUID userId) {
        User user = findUser(userId);
        Map<UUID, User> refs = loadRefs(Arrays.asList(user.getCreatedBy(), user.getLockedBy()));
        return new AdminUserDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getUserCode(),
                avatarUrls.large(user.getAvatarKey()),
                avatarUrls.thumb(user.getAvatarKey()),
                user.getSystemRole(),
                user.getStatus(),
                profileMapper.toSchoolRef(user.getSchool()),
                profileMapper.toGithub(githubRepository.findById(userId).orElse(null)),
                user.getBio(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getActivatedAt(),
                user.getLockedAt(),
                user.getLockedReason(),
                toRef(refs.get(user.getLockedBy())),
                toRef(refs.get(user.getCreatedBy())),
                (int) sessionRepository.countActiveByUserId(userId, Instant.now(clock)),
                user.getFailedLoginCount(),
                user.getLockedUntil(),
                user.getVersion(),
                user.getUpdatedAt());
    }

    /**
     * API-USER-14: sign-in events of a user, newest first, keyset paginated.
     *
     * @throws BusinessException {@code USER_NOT_FOUND}, or {@code VALIDATION_ERROR} for a broken cursor or limit
     */
    @Transactional(readOnly = true)
    public CursorResponse<AuthEventItemResponse> listAuthEvents(
            UUID userId, @Nullable String cursor, @Nullable Integer limit) {
        findUser(userId);
        int pageSize = Objects.requireNonNullElse(limit, DEFAULT_EVENT_LIMIT);
        if (pageSize < 1 || pageSize > MAX_EVENT_LIMIT) {
            throw validationError("limit", "OUT_OF_RANGE");
        }
        Optional<Position> after;
        try {
            after = CursorCodec.decode(cursor);
        } catch (IllegalArgumentException e) {
            throw validationError("cursor", "INVALID_FORMAT");
        }
        PageRequest page = PageRequest.of(0, pageSize + 1);
        List<AuthEvent> rows = after.map(position ->
                        authEventRepository.findPageByUserAfter(userId, position.createdAt(), position.id(), page))
                .orElseGet(() -> authEventRepository.findFirstPageByUser(userId, page));

        boolean hasMore = rows.size() > pageSize;
        List<AuthEvent> items = hasMore ? rows.subList(0, pageSize) : rows;
        Map<UUID, User> actors =
                loadRefs(items.stream().map(AuthEvent::getActorId).collect(Collectors.toSet()));
        String nextCursor = hasMore
                ? CursorCodec.encode(
                        items.getLast().getCreatedAt(), items.getLast().getId())
                : null;
        return new CursorResponse<>(
                items.stream().map(event -> toItem(event, actors)).toList(), nextCursor);
    }

    AdminUserSummaryResponse toSummary(User user) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getUserCode(),
                avatarUrls.thumb(user.getAvatarKey()),
                user.getSystemRole(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private AuthEventItemResponse toItem(AuthEvent event, Map<UUID, User> actors) {
        return new AuthEventItemResponse(
                event.getId(),
                event.getEventType(),
                event.getIpAddress(),
                event.getUserAgent() == null ? null : deviceLabels.parse(event.getUserAgent()),
                readJson(event.getMetadata()),
                actorOf(event, actors),
                event.getCreatedAt());
    }

    /** The Admin who acted, when it was not the account owner; immutable maps reject a null key */
    @Nullable
    private static UserRefResponse actorOf(AuthEvent event, Map<UUID, User> actors) {
        UUID actorId = event.getActorId();
        if (actorId == null || actorId.equals(event.getUserId())) {
            return null;
        }
        return toRef(actors.get(actorId));
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, JSON_OBJECT);
        } catch (JacksonException e) {
            return Map.of();
        }
    }

    private Map<UUID, User> loadRefs(Collection<@Nullable UUID> ids) {
        List<UUID> present = ids.stream().filter(Objects::nonNull).distinct().toList();
        return present.isEmpty()
                ? new HashMap<>()
                : userRepository.findAllById(present).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    @Nullable
    private static UserRefResponse toRef(@Nullable User user) {
        return user == null ? null : new UserRefResponse(user.getId(), user.getFullName(), user.getEmail());
    }

    /** Whitelisted by {@link AdminUserListParams}; users who never logged in sort last either way */
    private static Sort toSort(@Nullable String sort) {
        String[] parts = (sort == null ? DEFAULT_SORT : sort).split(",");
        Sort.Direction direction =
                parts.length > 1 && parts[1].equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort.Order order = new Sort.Order(direction, parts[0]);
        if (parts[0].equals("lastLoginAt")) {
            order = order.nullsLast();
        }
        return Sort.by(order, new Sort.Order(direction, "id"));
    }

    private static BusinessException validationError(String field, String code) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                Map.of(ProblemDetailFactory.ERRORS, List.of(new FieldError(field, code, null))));
    }
}
