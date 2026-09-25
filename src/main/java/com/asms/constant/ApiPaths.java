package com.asms.constant;

/**
 * API path constants. Controllers must use these constants in {@code @RequestMapping} instead of hard-coded strings.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public final class ApiPaths {

    public static final String API_V1 = "/api/v1";

    public static final String AUTH = API_V1 + "/auth";
    public static final String USERS = API_V1 + "/users";
    public static final String GROUPS = API_V1 + "/groups";
    public static final String ADMIN = API_V1 + "/admin";

    private ApiPaths() {}
}
