package com.asms.controller.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asms.TestcontainersConfiguration;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.entity.user.UserGithubAccount.GithubProfile;
import com.asms.repository.user.UserRepository;
import com.asms.service.user.GithubClient;
import com.asms.service.user.GithubClient.GithubUnavailableException;
import com.asms.support.TestUserCodes;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/** End-to-end flows of API-USER-17 to 20; GitHub itself is mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class GithubFlowIT {

    private static final String PASSWORD = "Secret123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final AtomicLong GITHUB_IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private GithubClient github;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        when(github.isConfigured()).thenReturn(true);
        when(github.clientId()).thenReturn("client-1");
        token = login(newUser());
    }

    @Test
    @DisplayName("UC-USER-01: authorize, callback, then the profile shows the account; the token is revoked")
    void connect_shouldStoreThePublicProfile() throws Exception {
        long githubId = GITHUB_IDS.incrementAndGet();
        when(github.exchangeCode(eq("code-1"), anyString())).thenReturn("gho_secret");
        when(github.currentUser("gho_secret")).thenReturn(profile(githubId, "nguyenvanan"));

        String state = authorize(token);
        authed(token, post("/api/v1/users/me/github/callback"), callbackBody("code-1", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("nguyenvanan"))
                .andExpect(jsonPath("$.publicRepos").value(24));

        verify(github).revokeToken("gho_secret");
        authed(token, get("/api/v1/users/me/profile"), null)
                .andExpect(jsonPath("$.github.login").value("nguyenvanan"));
        authed(token, post("/api/v1/users/me/github/authorize"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GITHUB_ALREADY_CONNECTED"));
    }

    @Test
    @DisplayName("BR-USER-22: a state works once and only for the user it was issued to")
    void callback_shouldRejectReusedOrForeignState() throws Exception {
        long githubId = GITHUB_IDS.incrementAndGet();
        when(github.exchangeCode(anyString(), anyString())).thenReturn("gho_x");
        when(github.currentUser("gho_x")).thenReturn(profile(githubId, "someone"));
        String state = authorize(token);
        String otherToken = login(newUser());

        authed(otherToken, post("/api/v1/users/me/github/callback"), callbackBody("c", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GITHUB_STATE_INVALID"));
        // The foreign attempt consumed the state: even its owner cannot use it any more
        authed(token, post("/api/v1/users/me/github/callback"), callbackBody("c", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GITHUB_STATE_INVALID"));
    }

    @Test
    @DisplayName("One GitHub account links to one ASMS user only")
    void callback_shouldRejectAccountInUse() throws Exception {
        long githubId = GITHUB_IDS.incrementAndGet();
        when(github.exchangeCode(anyString(), anyString())).thenReturn("gho_x");
        when(github.currentUser("gho_x")).thenReturn(profile(githubId, "shared"));
        authed(token, post("/api/v1/users/me/github/callback"), callbackBody("c", authorize(token)))
                .andExpect(status().isOk());

        String otherToken = login(newUser());
        authed(otherToken, post("/api/v1/users/me/github/callback"), callbackBody("c", authorize(otherToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GITHUB_ACCOUNT_IN_USE"));
    }

    @Test
    void callback_shouldReport502_whenGithubFails() throws Exception {
        when(github.exchangeCode(anyString(), anyString())).thenThrow(new GithubUnavailableException("down", null));

        authed(token, post("/api/v1/users/me/github/callback"), callbackBody("c", authorize(token)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("GITHUB_UNAVAILABLE"));
    }

    @Test
    @DisplayName("FR-USER-28: refresh copies the latest data, disconnect forgets the account")
    void refreshThenDisconnect() throws Exception {
        long githubId = GITHUB_IDS.incrementAndGet();
        when(github.exchangeCode(anyString(), anyString())).thenReturn("gho_x");
        when(github.currentUser("gho_x")).thenReturn(profile(githubId, "before"));
        authed(token, post("/api/v1/users/me/github/callback"), callbackBody("c", authorize(token)))
                .andExpect(status().isOk());
        when(github.userById(githubId)).thenReturn(Optional.of(profile(githubId, "after")));

        authed(token, post("/api/v1/users/me/github/refresh"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("after"));
        authed(token, post("/api/v1/users/me/github/refresh"), null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"));

        authed(token, delete("/api/v1/users/me/github"), null).andExpect(status().isNoContent());
        authed(token, delete("/api/v1/users/me/github"), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GITHUB_NOT_CONNECTED"));
    }

    private String authorize(String accessToken) throws Exception {
        String body = authed(accessToken, post("/api/v1/users/me/github/authorize"), null)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        URI url = URI.create(JsonPath.read(body, "$.authorizeUrl"));
        var params = UriComponentsBuilder.fromUri(url).build().getQueryParams();
        assertThat(params.getFirst("scope")).isEqualTo("read:user");
        assertThat(params.getFirst("redirect_uri")).endsWith("/settings/profile/github/callback");
        return params.getFirst("state");
    }

    private ResultActions authed(String accessToken, MockHttpServletRequestBuilder request, String body)
            throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private User newUser() {
        String email = "github" + System.nanoTime() + "@gmail.com";
        User user = User.createPending(email, "Nguyễn Văn A", TestUserCodes.codeFor(email), SystemRole.USER, null);
        user.activate(passwordEncoder.encode(PASSWORD), Instant.now());
        return userRepository.save(user);
    }

    private String login(User user) throws Exception {
        int n = SEQUENCE.incrementAndGet();
        String ip = "10.6." + (n / 250) + "." + (n % 250 + 1);
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCode\":\"%s\",\"password\":\"%s\"}".formatted(user.getUserCode(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String callbackBody(String code, String state) {
        return "{\"code\":\"%s\",\"state\":\"%s\"}".formatted(code, state);
    }

    private static GithubProfile profile(long githubId, String login) {
        return new GithubProfile(
                githubId,
                login,
                "Nguyen Van An",
                "https://avatars.githubusercontent.com/u/" + githubId,
                "https://github.com/" + login,
                "Frontend developer",
                "TP. Hồ Chí Minh",
                24,
                18,
                12,
                Instant.parse("2021-03-08T09:12:00Z"));
    }
}
