package com.asms.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asms.TestcontainersConfiguration;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.repository.user.UserRepository;
import com.asms.support.TestUserCodes;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** End-to-end flows of API-AUTH-01 to 05, 14 and 15 on real PostgreSQL and Redis. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthFlowIT {

    private static final String ORIGIN = "http://localhost:5173";
    private static final String PASSWORD = "Secret123";
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void createUser() {
        email = "user" + System.nanoTime() + "@gmail.com";
        userRepository.save(User.createBootstrapAdmin(
                email, TestUserCodes.codeFor(email), "Nguyen Van A", passwordEncoder.encode(PASSWORD), Instant.now()));
    }

    @Test
    @DisplayName("UC-AUTH-01: login returns an access token and sets the rt cookie")
    void login_shouldReturnTokenAndCookie() throws Exception {
        mockMvc.perform(loginRequest(email, PASSWORD, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(cookie().httpOnly("rt", true))
                .andExpect(cookie().path("rt", "/api/v1/auth"))
                .andExpect(cookie().sameSite("rt", "Strict"))
                .andExpect(cookie().maxAge("rt", 30 * 24 * 3600));
    }

    @Test
    @DisplayName("FR-AUTH-02: unknown email and wrong password give the same error")
    void login_shouldReturnSameError_forUnknownEmailAndWrongPassword() throws Exception {
        mockMvc.perform(loginRequest("nobody@gmail.com", PASSWORD, false))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        mockMvc.perform(loginRequest(email, "Wrong1234", false))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("BR-AUTH-04: five wrong passwords lock the account temporarily")
    void login_shouldLockAccount_afterFiveFailures() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(loginRequest(email, "Wrong1234", false)).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(loginRequest(email, "Wrong1234", false))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_TEMP_LOCKED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(900));
        mockMvc.perform(loginRequest(email, PASSWORD, false)).andExpect(status().isLocked());
    }

    @Test
    void login_shouldReturnFieldErrors_whenBodyInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCode\":\" \",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'userCode')].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].code").value("REQUIRED"));
    }

    @Test
    @DisplayName("UC-AUTH-02: refresh rotates the cookie; the old cookie is then rejected as reuse")
    void refresh_shouldRotateAndDetectReuse() throws Exception {
        Cookie firstCookie = login(email).getResponse().getCookie("rt");

        MvcResult refreshed = mockMvc.perform(refreshRequest(firstCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();
        Cookie secondCookie = refreshed.getResponse().getCookie("rt");
        assertThat(secondCookie.getValue()).isNotEqualTo(firstCookie.getValue());

        // Within the 5-second race window a reused token is reported as a race
        mockMvc.perform(refreshRequest(firstCookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_RACE"));
    }

    @Test
    @DisplayName("NFR-AUTH-07: refresh without an allowed Origin is rejected")
    void refresh_shouldRejectMissingOrigin() throws Exception {
        Cookie rt = login(email).getResponse().getCookie("rt");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(rt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void refresh_shouldReject_whenNoCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
    }

    @Test
    @DisplayName("FR-AUTH-10: after logout the access token is blocked immediately")
    void logout_shouldBlockAccessTokenAndClearCookie() throws Exception {
        MvcResult login = login(email);
        String accessToken = accessToken(login);
        Cookie rt = login.getResponse().getCookie("rt");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(rt))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("rt", 0));

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_TOKEN_INVALID"));
        mockMvc.perform(refreshRequest(rt)).andExpect(status().isUnauthorized());
    }

    @Test
    void me_shouldRequireAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_TOKEN_MISSING"));
    }

    @Test
    void me_shouldReturnCurrentUser() throws Exception {
        String accessToken = accessToken(login(email));

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.systemRole").value("ADMIN"));
    }

    @Test
    @DisplayName("UC-AUTH-09: list devices and log out another one")
    void sessions_shouldListAndRevokeOtherDevice() throws Exception {
        String otherDeviceToken = accessToken(login(email));
        String currentToken = accessToken(login(email));

        MvcResult list = mockMvc.perform(
                        get("/api/v1/auth/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + currentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[1].current").value(false))
                .andReturn();
        String otherSessionId = JsonPath.read(list.getResponse().getContentAsString(), "$[1].id");

        mockMvc.perform(delete("/api/v1/auth/sessions/" + otherSessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherDeviceToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-AUTH-11: logout-all blocks every device, including the current one")
    void logoutAll_shouldBlockEveryDevice() throws Exception {
        String firstToken = accessToken(login(email));
        String secondToken = accessToken(login(email));

        mockMvc.perform(post("/api/v1/auth/logout-all").header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("BR-AUTH-15: a USER cannot reach admin endpoints")
    void adminEndpoints_shouldRejectUserRole() throws Exception {
        String userEmail = "plain" + System.nanoTime() + "@gmail.com";
        User user =
                User.createPending(userEmail, "Plain User", TestUserCodes.codeFor(userEmail), SystemRole.USER, null);
        user.activate(passwordEncoder.encode(PASSWORD), Instant.now());
        userRepository.save(user);
        String accessToken = accessToken(login(userEmail));

        mockMvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    private MvcResult login(String loginEmail) throws Exception {
        return mockMvc.perform(loginRequest(loginEmail, PASSWORD, false))
                .andExpect(status().isOk())
                .andReturn();
    }

    // A distinct client IP per call keeps the per-IP login limit (BR-AUTH-09) out of the way
    private static MockHttpServletRequestBuilder loginRequest(String loginEmail, String password, boolean rememberMe) {
        String ip = "10.1." + (IP_SEQUENCE.incrementAndGet() / 250) + "." + (IP_SEQUENCE.get() % 250 + 1);
        return post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userCode":"%s","password":"%s","rememberMe":%s}
                        """.formatted(TestUserCodes.codeFor(loginEmail), password, rememberMe));
    }

    private static MockHttpServletRequestBuilder refreshRequest(Cookie rt) {
        return post("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN).cookie(rt);
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
