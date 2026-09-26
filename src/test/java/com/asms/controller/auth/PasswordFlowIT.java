package com.asms.controller.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asms.TestcontainersConfiguration;
import com.asms.entity.auth.UserTokenType;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.repository.user.UserRepository;
import com.asms.service.auth.AuthMailService;
import com.asms.service.auth.UserTokenService;
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

/** End-to-end flows of API-AUTH-06 to 11 on real PostgreSQL and Redis; emails are captured instead of sent. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PasswordFlowIT {

    private static final String PASSWORD = "Secret123";
    private static final String NEW_PASSWORD = "Changed456";
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTokenService userTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AuthMailService authMailService;

    private String email;

    @BeforeEach
    void createUser() {
        email = "user" + System.nanoTime() + "@gmail.com";
        userRepository.save(
                User.createBootstrapAdmin(email, "Nguyen Van A", passwordEncoder.encode(PASSWORD), Instant.now()));
    }

    @Test
    @DisplayName("UC-AUTH-04: forgot, validate, reset, then the old session is gone and the link is spent")
    void resetFlow_shouldChangePasswordAndRevokeSessions() throws Exception {
        String oldAccessToken = accessToken(login(email, PASSWORD));

        postJson("/api/v1/auth/password/forgot", "{\"email\":\"%s\"}".formatted(email.toUpperCase()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        String token = capturedResetToken();

        postJson("/api/v1/auth/password/reset/validate", tokenBody(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedEmail").value(email.substring(0, 2) + "***@gmail.com"));
        postJson(
                        "/api/v1/auth/password/reset",
                        "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, NEW_PASSWORD))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldAccessToken))
                .andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
        postJson("/api/v1/auth/password/reset/validate", tokenBody(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.reason").value("USED"));
        verify(authMailService).sendPasswordChanged(any(), any());
    }

    @Test
    @DisplayName("FR-AUTH-12: an unknown email gets exactly the same answer")
    void forgot_shouldAnswerSame_whenEmailUnknown() throws Exception {
        postJson("/api/v1/auth/password/forgot", "{\"email\":\"nobody@gmail.com\"}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(authMailService, never()).sendPasswordReset(any(), any(), any());
    }

    @Test
    void reset_shouldReturnViolations_whenPasswordWeak() throws Exception {
        postJson("/api/v1/auth/password/forgot", "{\"email\":\"%s\"}".formatted(email))
                .andExpect(status().isAccepted());
        String token = capturedResetToken();

        postJson("/api/v1/auth/password/reset", "{\"token\":\"%s\",\"newPassword\":\"abcdefgh\"}".formatted(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_POLICY"))
                .andExpect(jsonPath("$.violations[0]").value("DIGIT"));
    }

    @Test
    @DisplayName("UC-AUTH-05: activate a pending account, then the link reports the account as active")
    void activationFlow_shouldActivateAccount() throws Exception {
        String pendingEmail = "pending" + System.nanoTime() + "@gmail.com";
        User pending = userRepository.save(User.createPending(pendingEmail, "Tran Thi B", null, SystemRole.USER, null));
        String token = userTokenService
                .issue(pending, UserTokenType.ACTIVATION, null, null)
                .rawToken();

        postJson("/api/v1/auth/activation/validate", tokenBody(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(pendingEmail))
                .andExpect(jsonPath("$.fullName").value("Tran Thi B"));
        login(pendingEmail, PASSWORD).andExpect(status().isUnauthorized());

        postJson("/api/v1/auth/activate", "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, PASSWORD))
                .andExpect(status().isNoContent());

        login(pendingEmail, PASSWORD).andExpect(status().isOk());
        postJson("/api/v1/auth/activation/validate", tokenBody(token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_ALREADY_ACTIVE"));
    }

    @Test
    void activation_shouldRejectUnknownToken() throws Exception {
        postJson("/api/v1/auth/activation/validate", tokenBody("unknown-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("UC-AUTH-06: change keeps this device signed in and logs out the others")
    void changePassword_shouldKeepCurrentSessionOnly() throws Exception {
        String otherDevice = accessToken(login(email, PASSWORD));
        String thisDevice = accessToken(login(email, PASSWORD));

        mockMvc.perform(put("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + thisDevice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}"
                                .formatted(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + thisDevice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangedAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherDevice))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_shouldReject_whenCurrentPasswordWrong() throws Exception {
        String accessToken = accessToken(login(email, PASSWORD));

        mockMvc.perform(put("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Wrong1234\",\"newPassword\":\"%s\"}".formatted(NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_CURRENT_PASSWORD_WRONG"));
    }

    private String capturedResetToken() {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(authMailService).sendPasswordReset(any(), token.capture(), any());
        return token.getValue();
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(
                withClientIp(post(path)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String loginEmail, String password) throws Exception {
        return postJson("/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(loginEmail, password));
    }

    // A distinct client IP per call keeps the per-IP limits (BR-AUTH-09, BR-AUTH-10) out of the way
    private static MockHttpServletRequestBuilder withClientIp(MockHttpServletRequestBuilder builder) {
        int n = IP_SEQUENCE.incrementAndGet();
        String ip = "10.2." + (n / 250) + "." + (n % 250 + 1);
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    private static String tokenBody(String token) {
        return "{\"token\":\"%s\"}".formatted(token);
    }

    private static String accessToken(ResultActions login) throws Exception {
        return JsonPath.read(login.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }
}
