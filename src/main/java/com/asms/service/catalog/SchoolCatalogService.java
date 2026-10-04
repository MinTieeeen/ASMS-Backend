package com.asms.service.catalog;

import com.asms.dto.catalog.SchoolListResponse;
import com.asms.mapper.user.ProfileMapper;
import com.asms.repository.catalog.SchoolRepository;
import com.asms.util.TokenHasher;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Active schools for the school picker (API-USER-21, BR-USER-03). The list is small and read often, so it is cached in
 * Redis under {@code catalog:schools:active} for an hour and served with an ETag; Admin writes clear the cache
 * (API-USER-23 to 25). When Redis is down the list is read from the database.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolCatalogService {

    static final String ACTIVE_SCHOOLS_KEY = "catalog:schools:active";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final SchoolRepository schoolRepository;
    private final ProfileMapper profileMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ActiveSchools activeSchools() {
        String json = readCache();
        if (json == null) {
            SchoolListResponse list = new SchoolListResponse(schoolRepository.findByActiveTrueOrderByNameAsc().stream()
                    .map(profileMapper::toSchoolRef)
                    .toList());
            json = objectMapper.writeValueAsString(list);
            writeCache(json);
            return new ActiveSchools(list, etagOf(json));
        }
        try {
            return new ActiveSchools(objectMapper.readValue(json, SchoolListResponse.class), etagOf(json));
        } catch (JacksonException e) {
            log.warn("Unreadable cache {}, reloading: {}", ACTIVE_SCHOOLS_KEY, e.getMessage());
            evict();
            return activeSchools();
        }
    }

    /** Called after every Admin write on schools, once the transaction committed */
    public void evict() {
        try {
            redis.delete(ACTIVE_SCHOOLS_KEY);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, {} not cleared: {}", ACTIVE_SCHOOLS_KEY, e.getMessage());
        }
    }

    @Nullable
    private String readCache() {
        try {
            return redis.opsForValue().get(ACTIVE_SCHOOLS_KEY);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, reading schools from the database: {}", e.getMessage());
            return null;
        }
    }

    private void writeCache(String json) {
        try {
            redis.opsForValue().set(ACTIVE_SCHOOLS_KEY, json, CACHE_TTL);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, {} not cached: {}", ACTIVE_SCHOOLS_KEY, e.getMessage());
        }
    }

    /** Strong ETag of the exact JSON served */
    private static String etagOf(String json) {
        return "\"" + TokenHasher.sha256Hex(json).substring(0, 32) + "\"";
    }

    /** The list and its ETag */
    public record ActiveSchools(SchoolListResponse body, String etag) {}
}
