package com.asms.mapper.user;

import com.asms.dto.admin.AdminUserResponse;
import com.asms.dto.auth.CurrentUserResponse;
import com.asms.entity.user.User;
import com.asms.service.user.AvatarUrlResolver;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps {@link User} to API responses.
 *
 * @author MinhTien
 * @version 1.1.0
 * @since 2026-09-26
 * @modified 2026-09-27
 */
@Mapper(uses = AvatarUrlResolver.class, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface UserMapper {

    @Mapping(target = "avatarUrl", source = "avatarKey", qualifiedByName = AvatarUrlResolver.LARGE)
    CurrentUserResponse toCurrentUser(User user);

    AdminUserResponse toAdminResponse(User user);
}
