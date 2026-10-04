package com.asms.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.asms.entity.user.UserGithubAccount.GithubProfile;
import com.asms.service.user.GithubClient.GithubUnavailableException;
import com.asms.support.TestProperties;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GithubClientTest {

    private static final String USER_JSON = """
            {"id": 58312004, "login": "nguyenvanan", "name": "Nguyen Van An",
             "avatar_url": "https://avatars.githubusercontent.com/u/58312004",
             "html_url": "https://github.com/nguyenvanan", "bio": "Frontend developer",
             "location": "TP. Hồ Chí Minh", "public_repos": 24, "followers": 18, "following": 12,
             "created_at": "2021-03-08T09:12:00Z", "email": "hidden@example.com"}
            """;

    private MockRestServiceServer server;
    private GithubClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GithubClient(TestProperties.appProperties(), builder);
    }

    @Test
    void exchangeCode_shouldReturnTheAccessToken() {
        server.expect(requestTo("https://github.test/login/oauth/access_token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=abc")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"gho_x\",\"scope\":\"read:user\"}", MediaType.APPLICATION_JSON));

        assertThat(client.exchangeCode("abc", "http://localhost:5173/cb")).isEqualTo("gho_x");
    }

    @Test
    void exchangeCode_shouldFail_whenGithubRefusesTheCode() {
        server.expect(requestTo("https://github.test/login/oauth/access_token"))
                .andRespond(withSuccess("{\"error\":\"bad_verification_code\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchangeCode("old", "http://localhost:5173/cb"))
                .isInstanceOf(GithubUnavailableException.class);
    }

    @Test
    void currentUser_shouldMapThePublicProfile() {
        server.expect(requestTo("https://api.github.test/user"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer gho_x"))
                .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));

        GithubProfile profile = client.currentUser("gho_x");

        assertThat(profile.githubId()).isEqualTo(58312004L);
        assertThat(profile.login()).isEqualTo("nguyenvanan");
        assertThat(profile.publicRepos()).isEqualTo(24);
        assertThat(profile.createdAt()).isEqualTo(Instant.parse("2021-03-08T09:12:00Z"));
    }

    @Test
    void userById_shouldBeEmpty_whenTheAccountWasDeleted() {
        server.expect(requestTo("https://api.github.test/user/7"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Basic ")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.userById(7)).isEmpty();
    }

    @Test
    void userById_shouldFail_whenGithubIsDown() {
        server.expect(requestTo("https://api.github.test/user/7")).andRespond(withServerError());

        assertThatThrownBy(() -> client.userById(7)).isInstanceOf(GithubUnavailableException.class);
    }

    @Test
    void revokeToken_shouldNeverThrow() {
        server.expect(requestTo("https://api.github.test/applications/test-client-id/token"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        client.revokeToken("gho_x");

        server.verify();
    }
}
