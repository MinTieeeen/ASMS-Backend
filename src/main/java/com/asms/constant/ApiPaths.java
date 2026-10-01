package com.asms.constant;

/**
 * API path constants. Controllers must use these constants in {@code @RequestMapping} instead of hard-coded strings.
 *
 * @author MinhTien
 * @version 1.1.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public final class ApiPaths {

    public static final String API_V1 = "/api/v1";

    public static final String AUTH = API_V1 + "/auth";
    public static final String USERS = API_V1 + "/users";
    public static final String GROUPS = API_V1 + "/groups";
    public static final String ADMIN = API_V1 + "/admin";
    public static final String ADMIN_USERS = ADMIN + "/users";

    /** Sub-paths of {@link #USERS}. */
    public static final class Users {

        public static final String ME_LANGUAGE = "/me/language";

        private Users() {}
    }

    /** Sub-paths of {@link #ADMIN_USERS} (API-AUTH-12, API-AUTH-13). */
    public static final class AdminUsers {

        public static final String ACTIVATION_EMAIL = "/{userId}/activation-email";

        private AdminUsers() {}
    }

    /** Sub-paths of {@link #AUTH} (Auth specification section 8.2). */
    public static final class Auth {

        public static final String LOGIN = "/login";
        public static final String REFRESH = "/refresh";
        public static final String LOGOUT = "/logout";
        public static final String LOGOUT_ALL = "/logout-all";
        public static final String ME = "/me";
        public static final String PASSWORD = "/password";
        public static final String PASSWORD_FORGOT = "/password/forgot";
        public static final String PASSWORD_RESET_VALIDATE = "/password/reset/validate";
        public static final String PASSWORD_RESET = "/password/reset";
        public static final String ACTIVATION_VALIDATE = "/activation/validate";
        public static final String ACTIVATE = "/activate";
        public static final String SESSIONS = "/sessions";
        public static final String SESSION = "/sessions/{sessionId}";

        private Auth() {}
    }

    private ApiPaths() {}
}
