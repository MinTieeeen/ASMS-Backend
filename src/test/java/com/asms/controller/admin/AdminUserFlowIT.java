package com.asms.controller.admin;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asms.TestcontainersConfiguration;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.repository.user.UserRepository;
import com.asms.service.auth.AuthMailService;
import com.asms.support.TestUserCodes;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** End-to-end flows of API-AUTH-12, API-AUTH-13, the admin user list and the language setting. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminUserFlowIT {

    private static final String PASSWORD = "Secret123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AuthMailService authMailService;

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        String adminEmail = uniqueEmail("admin");
        userRepository.save(User.createBootstrapAdmin(
                adminEmail,
                TestUserCodes.codeFor(adminEmail),
                "Quan Tri",
                passwordEncoder.encode(PASSWORD),
                Instant.now()));
        adminToken = accessToken(login(adminEmail));
    }

    @Test
    @DisplayName("UC-AUTH-07 + UC-AUTH-05: Admin creates an account, the user activates it and logs in")
    void createUser_thenActivate_thenLogin() throws Exception {
        String email = uniqueEmail("new");

        asAdmin(post("/api/v1/admin/users"), createBody(email, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.language").value("EN"));
        String token = capturedActivationToken();

        mockMvc.perform(withIp(post("/api/v1/auth/activate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, PASSWORD)))
                .andExpect(status().isNoContent());
        login(email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.language").value("EN"))
                .andExpect(jsonPath("$.user.userCode").value(TestUserCodes.codeFor(email)));
    }

    @Test
    @DisplayName("FR-AUTH-20: duplicate email and user ID are rejected with their own codes")
    void createUser_shouldRejectDuplicates() throws Exception {
        String email = uniqueEmail("dup");
        String userCode = "DUP" + SEQUENCE.incrementAndGet();
        asAdmin(post("/api/v1/admin/users"), createBody(email, userCode.toLowerCase()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userCode").value(userCode));

        asAdmin(post("/api/v1/admin/users"), createBody(email.toUpperCase(), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EMAIL_EXISTS"));
        asAdmin(post("/api/v1/admin/users"), createBody(uniqueEmail("other"), userCode))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_CODE_EXISTS"));
    }

    @Test
    void createUser_shouldReturnFieldErrors_whenDataInvalid() throws Exception {
        asAdmin(post("/api/v1/admin/users"), """
                        {"email":"%s","fullName":" A ","userCode":"21-120","systemRole":"USER"}
                        """.formatted(uniqueEmail("bad")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'fullName')].code").value("INVALID_LENGTH"))
                .andExpect(jsonPath("$.errors[?(@.field == 'userCode')].code").value("INVALID_FORMAT"));
    }

    @Test
    @DisplayName("UC-AUTH-08: resend works while pending, then fails once the account is active")
    void resendActivation_shouldFollowAccountStatus() throws Exception {
        User pending = userRepository.save(User.createPending(
                uniqueEmail("pending"),
                "Pending User",
                TestUserCodes.codeFor(uniqueEmail("pending")),
                SystemRole.USER,
                null));

        asAdmin(post("/api/v1/admin/users/" + pending.getId() + "/activation-email"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activationExpiresAt").isNotEmpty());

        pending.activate(passwordEncoder.encode(PASSWORD), Instant.now());
        userRepository.save(pending);
        asAdmin(post("/api/v1/admin/users/" + pending.getId() + "/activation-email"), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_NOT_PENDING"));
    }

    @Test
    void listUsers_shouldFilterByKeywordAndStatus() throws Exception {
        String marker = "zz" + SEQUENCE.incrementAndGet();
        userRepository.save(User.createPending(
                marker + "@gmail.com",
                "Keyword User",
                TestUserCodes.codeFor(marker + "@gmail.com"),
                SystemRole.USER,
                null));

        asAdmin(get("/api/v1/admin/users").param("keyword", marker).param("status", "PENDING_ACTIVATION"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].email").value(marker + "@gmail.com"));
        asAdmin(get("/api/v1/admin/users").param("size", "5"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems", greaterThanOrEqualTo(2)));
        asAdmin(get("/api/v1/admin/users").param("sort", "unknownField"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("BR-AUTH-15: a USER cannot create accounts")
    void createUser_shouldRejectUserRole() throws Exception {
        String userEmail = uniqueEmail("plain");
        User user =
                User.createPending(userEmail, "Plain User", TestUserCodes.codeFor(userEmail), SystemRole.USER, null);
        user.activate(passwordEncoder.encode(PASSWORD), Instant.now());
        userRepository.save(user);
        String userToken = accessToken(login(userEmail));

        mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(uniqueEmail("x"), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("NFR14: the user changes the language of emails and the UI")
    void updateMyLanguage_shouldPersistLanguage() throws Exception {
        asAdmin(put("/api/v1/users/me/language"), "{\"language\":\"EN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("EN"));
        asAdmin(get("/api/v1/auth/me"), null).andExpect(jsonPath("$.language").value("EN"));

        asAdmin(put("/api/v1/users/me/language"), "{\"language\":\"FR\"}").andExpect(status().isBadRequest());
        asAdmin(put("/api/v1/users/me/language"), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("language"));
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(withIp(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"userCode\":\"%s\",\"password\":\"%s\"}".formatted(TestUserCodes.codeFor(email), PASSWORD)));
    }

    private String capturedActivationToken() {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(authMailService).sendActivation(any(), token.capture(), any());
        return token.getValue();
    }

    /** {@code userCode} null: the code the account logs in with, see {@link TestUserCodes} */
    private static String createBody(String email, String userCode) {
        String code = userCode == null ? TestUserCodes.codeFor(email) : userCode;
        return """
                {"email":"%s","fullName":"Tran Thi B","userCode":"%s","systemRole":"USER","language":"EN"}
                """.formatted(email, code);
    }

    // A distinct client IP per login keeps the per-IP login limit (BR-AUTH-09) out of the way
    private static MockHttpServletRequestBuilder withIp(MockHttpServletRequestBuilder builder) {
        int n = SEQUENCE.incrementAndGet();
        String ip = "10.3." + (n / 250) + "." + (n % 250 + 1);
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    private static String uniqueEmail(String prefix) {
        return prefix + System.nanoTime() + "@gmail.com";
    }

    private static String accessToken(ResultActions login) throws Exception {
        return JsonPath.read(login.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }
}
