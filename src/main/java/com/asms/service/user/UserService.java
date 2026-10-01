package com.asms.service.user;

import com.asms.dto.auth.CurrentUserResponse;
import com.asms.entity.user.Language;
import com.asms.entity.user.User;
import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.asms.mapper.user.UserMapper;
import com.asms.repository.user.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Personal settings of the signed-in user. Today only the preferred language, used for emails and the UI (NFR14).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /** @return the updated user, so the frontend can refresh its store in one round trip */
    @Transactional
    public CurrentUserResponse changeLanguage(UUID userId, Language language) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changeLanguage(language);
        return userMapper.toCurrentUser(user);
    }
}
