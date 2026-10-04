package com.asms.service.user;

import com.asms.config.AppProperties;
import com.asms.entity.user.UserGithubAccount.GithubProfile;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls to GitHub for the account connection (API-USER-17 to 20). Every failure of GitHub itself (network, 5xx,
 * rejected code) surfaces as {@link GithubUnavailableException}; the service turns it into {@code GITHUB_UNAVAILABLE}.
 *
 * <p>The user's OAuth token only lives in memory while {@link #exchangeCode} and {@link #currentUser} run; it is revoked
 * right after with {@link #revokeToken}. Later refreshes read the public profile with the app's own credentials.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Slf4j
@Component
public class GithubClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final String API_VERSION = "2022-11-28";

    private final AppProperties.Github config;
    private final RestClient oauth;
    private final RestClient api;

    @Autowired
    public GithubClient(AppProperties props) {
        this(props, RestClient.builder().requestFactory(requestFactory()));
    }

    /** Lets tests bind a mock server to the builder */
    GithubClient(AppProperties props, RestClient.Builder builder) {
        this.config = props.github();
        this.oauth = builder.clone().baseUrl(config.oauthBaseUrl()).build();
        this.api = builder.clone()
                .baseUrl(config.apiBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", API_VERSION)
                .build();
    }

    public boolean isConfigured() {
        return config.isConfigured();
    }

    public String clientId() {
        return config.isConfigured() && config.clientId() != null ? config.clientId() : "";
    }

    /** Trades the code GitHub sent back for an access token */
    public String exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        try {
            TokenJson token = oauth.post()
                    .uri("/login/oauth/access_token")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenJson.class);
            // GitHub answers 200 with {"error": "bad_verification_code"} for an expired or reused code
            if (token == null || token.accessToken() == null) {
                throw new GithubUnavailableException(
                        "Code exchange refused: " + (token == null ? "empty body" : token.error()), null);
            }
            return token.accessToken();
        } catch (RestClientException e) {
            throw new GithubUnavailableException("Code exchange failed", e);
        }
    }

    /** {@code GET /user} with the user's token */
    public GithubProfile currentUser(String accessToken) {
        try {
            return toProfile(api.get()
                    .uri("/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(UserJson.class));
        } catch (RestClientException e) {
            throw new GithubUnavailableException("Reading /user failed", e);
        }
    }

    /** {@code GET /user/{id}} with the app credentials (API-USER-19); empty when the account no longer exists */
    public Optional<GithubProfile> userById(long githubId) {
        try {
            return Optional.of(toProfile(api.get()
                    .uri("/user/{id}", githubId)
                    .headers(headers -> headers.setBasicAuth(config.clientId(), config.clientSecret()))
                    .retrieve()
                    .body(UserJson.class)));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new GithubUnavailableException("Reading /user/" + githubId + " failed", e);
        }
    }

    /** Revokes the user's token at once (API-USER-18 step 4); a failure is only logged */
    public void revokeToken(String accessToken) {
        try {
            api.method(HttpMethod.DELETE)
                    .uri("/applications/{clientId}/token", config.clientId())
                    .headers(headers -> headers.setBasicAuth(config.clientId(), config.clientSecret()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("access_token", accessToken))
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (request, response) -> {})
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("GitHub token not revoked, it expires on its own: {}", e.getMessage());
        }
    }

    private static GithubProfile toProfile(@Nullable UserJson user) {
        if (user == null || user.id() <= 0 || user.login() == null) {
            throw new GithubUnavailableException("Unexpected /user body", null);
        }
        return new GithubProfile(
                user.id(),
                user.login(),
                truncate(user.name(), 255),
                user.avatarUrl() == null ? "https://avatars.githubusercontent.com/u/" + user.id() : user.avatarUrl(),
                user.htmlUrl() == null ? "https://github.com/" + user.login() : user.htmlUrl(),
                truncate(user.bio(), 500),
                truncate(user.location(), 255),
                Math.max(0, user.publicRepos()),
                Math.max(0, user.followers()),
                Math.max(0, user.following()),
                user.createdAt() == null ? Instant.EPOCH : user.createdAt());
    }

    @Nullable
    private static String truncate(@Nullable String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenJson(
            @JsonProperty("access_token") @Nullable String accessToken,
            @JsonProperty("error") @Nullable String error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserJson(
            long id,
            @Nullable String login,
            @Nullable String name,
            @JsonProperty("avatar_url") @Nullable String avatarUrl,
            @JsonProperty("html_url") @Nullable String htmlUrl,
            @Nullable String bio,
            @Nullable String location,
            @JsonProperty("public_repos") int publicRepos,
            int followers,
            int following,
            @JsonProperty("created_at") @Nullable Instant createdAt) {}

    /** GitHub did not answer, or answered something unusable */
    public static class GithubUnavailableException extends RuntimeException {

        public GithubUnavailableException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }
}
