package com.asms.listener.auth;

import com.asms.entity.auth.AuthEvent;
import com.asms.event.auth.AuthEventOccurred;
import com.asms.repository.auth.AuthEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Persists {@link AuthEventOccurred} into {@code auth_events} (FA-12, FR-AUTH-23).
 *
 * <p>Runs asynchronously after the publishing transaction commits (or immediately when there is none). A failure is
 * logged and swallowed: the event log must never make an API call fail (section 7.8 of the table description).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventListener {

    private final AuthEventRepository authEventRepository;
    private final ObjectMapper objectMapper;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onAuthEvent(AuthEventOccurred event) {
        try {
            authEventRepository.save(AuthEvent.of(
                    event.type(),
                    event.userId(),
                    event.actorId(),
                    event.email(),
                    event.sessionId(),
                    event.ipAddress(),
                    event.userAgent(),
                    objectMapper.writeValueAsString(event.metadata())));
        } catch (RuntimeException e) {
            log.warn("Could not store auth event {} for user {}", event.type(), event.userId(), e);
        }
    }
}
