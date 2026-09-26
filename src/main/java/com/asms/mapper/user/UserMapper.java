package com.asms.mapper.user;

import com.asms.dto.auth.CurrentUserResponse;
import com.asms.entity.user.User;
import org.mapstruct.Mapper;

/**
 * Maps {@link User} to API responses.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Mapper
public interface UserMapper {

    CurrentUserResponse toCurrentUser(User user);
}
