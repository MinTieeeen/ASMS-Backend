package com.asms.mapper.auth;

import com.asms.dto.auth.SessionResponse;
import com.asms.entity.auth.UserSession;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps {@link UserSession} to the device list item (API-AUTH-14). The IP shown is the one of the latest refresh.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Mapper
public interface SessionMapper {

    @Mapping(target = "id", source = "session.id")
    @Mapping(target = "deviceLabel", source = "session.deviceLabel")
    @Mapping(target = "ipAddress", source = "session.lastIpAddress")
    @Mapping(target = "createdAt", source = "session.createdAt")
    @Mapping(target = "lastUsedAt", source = "session.lastUsedAt")
    @Mapping(target = "current", expression = "java(session.getId().equals(currentSessionId))")
    SessionResponse toResponse(UserSession session, UUID currentSessionId);
}
