package com.asms.controller.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asms.TestcontainersConfiguration;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.repository.user.UserRepository;
import com.asms.service.storage.StorageService;
import com.asms.support.TestUserCodes;
import com.jayway.jsonpath.JsonPath;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** End-to-end flows of API-USER-03 and API-USER-04; the object storage is mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.storage.public-base-url=https://cdn.test")
class AvatarFlowIT {

    private static final String PASSWORD = "Secret123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private StorageService storage;

    private User user;
    private String token;

    @BeforeEach
    void loginAsUser() throws Exception {
        when(storage.isConfigured()).thenReturn(true);
        String email = "avatar" + System.nanoTime() + "@gmail.com";
        User pending = User.createPending(email, "Nguyễn Văn A", TestUserCodes.codeFor(email), SystemRole.USER, null);
        pending.activate(passwordEncoder.encode(PASSWORD), Instant.now());
        user = userRepository.save(pending);
        String body = mockMvc.perform(withIp(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCode\":\"%s\",\"password\":\"%s\"}"
                                .formatted(TestUserCodes.codeFor(email), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        token = JsonPath.read(body, "$.accessToken");
    }

    @Test
    @DisplayName("UC-USER-02: upload stores two WebP files, the profile points to them, a new upload removes the old")
    void upload_shouldStoreFilesAndReplaceThePreviousOne() throws Exception {
        String prefix = "https://cdn.test/avatars/" + user.getId() + "/";
        String first = upload(png(400, 300), 50, 0, 300)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl", startsWith(prefix)))
                .andExpect(jsonPath("$.avatarUrl", endsWith("-256.webp")))
                .andExpect(jsonPath("$.avatarThumbUrl", endsWith("-64.webp")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstKey = keyOf(JsonPath.read(first, "$.avatarUrl"));
        verify(storage).put(eq(firstKey + "-256.webp"), any(), eq("image/webp"), anyString());
        verify(storage).put(eq(firstKey + "-64.webp"), any(), eq("image/webp"), anyString());
        verify(storage, never()).delete(any());
        authed(get("/api/v1/auth/me"))
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.test/" + firstKey + "-256.webp"));

        upload(png(200, 200), 0, 0, 200).andExpect(status().isOk());

        verify(storage, timeout(2000)).delete(List.of(firstKey + "-256.webp", firstKey + "-64.webp"));
    }

    @Test
    @DisplayName("API-USER-04: removing the photo clears the key and deletes the files")
    void remove_shouldClearAvatar() throws Exception {
        String body = upload(png(200, 200), 0, 0, 200).andReturn().getResponse().getContentAsString();
        String key = keyOf(JsonPath.read(body, "$.avatarUrl"));

        authed(delete("/api/v1/users/me/avatar")).andExpect(status().isNoContent());

        authed(get("/api/v1/users/me/profile"))
                .andExpect(jsonPath("$.avatarUrl").value(nullValue()));
        verify(storage, timeout(2000)).delete(List.of(key + "-256.webp", key + "-64.webp"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getAvatarKey())
                .isNull();
    }

    @Test
    void upload_shouldRejectBadFiles() throws Exception {
        upload("not an image".getBytes(), 0, 0, 128)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("AVATAR_UNSUPPORTED_TYPE"));
        upload(png(300, 300), 200, 0, 200)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVATAR_INVALID_IMAGE"));
        upload(new byte[5 * 1024 * 1024 + 1], 0, 0, 128)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("AVATAR_TOO_LARGE"));
        authed(multipart(HttpMethod.PUT, "/api/v1/users/me/avatar").file(file(png(200, 200))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(storage, never()).put(anyString(), any(), anyString(), anyString());
    }

    private ResultActions upload(byte[] content, int cropX, int cropY, int cropSize) throws Exception {
        return authed(multipart(HttpMethod.PUT, "/api/v1/users/me/avatar")
                .file(file(content))
                .param("cropX", String.valueOf(cropX))
                .param("cropY", String.valueOf(cropY))
                .param("cropSize", String.valueOf(cropSize)));
    }

    private ResultActions authed(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "photo.png", "image/png", content);
    }

    /** "https://cdn.test/avatars/u/k-256.webp" → "avatars/u/k" */
    private static String keyOf(String url) {
        String path = url.substring("https://cdn.test/".length());
        return path.substring(0, path.lastIndexOf('-'));
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.createGraphics().setColor(Color.ORANGE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    // A distinct client IP per login keeps the per-IP login limit (BR-AUTH-09) out of the way
    private static MockHttpServletRequestBuilder withIp(MockHttpServletRequestBuilder builder) {
        int n = SEQUENCE.incrementAndGet();
        String ip = "10.5." + (n / 250) + "." + (n % 250 + 1);
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }
}
