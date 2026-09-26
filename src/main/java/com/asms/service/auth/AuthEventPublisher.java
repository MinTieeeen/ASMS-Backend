package com.asms.service.auth;

import com.asms.event.auth.AuthEventOccurred;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Entry point for Auth services to record an authentication event (FA-12). Events published inside a transaction are
 * stored only if it commits.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Component
@RequiredArgsConstructor
public class AuthEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void publish(AuthEventOccurred event) {
        publisher.publishEvent(event);
    }
}
