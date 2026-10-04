package com.asms.controller.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asms.TestcontainersConfiguration;
import com.asms.entity.catalog.School;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.repository.catalog.SchoolRepository;
import com.asms.repository.user.UserRepository;
import com.asms.support.TestUserCodes;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** End-to-end flows of API-USER-01, 02, 05 and 21. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProfileFlowIT {

    private static final String PASSWORD = "Secret123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;
    private String token;

    @BeforeEach
    void loginAsUser() throws Exception {
        String email = "profile" + System.nanoTime() + "@gmail.com";
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
    @DisplayName("UC-USER-01: read, then change only the sent fields with the version read")
    void updateMyProfile_shouldChangeSentFieldsOnly() throws Exception {
        String school = schoolRepository
                .findByActiveTrueOrderByNameAsc()
                .getFirst()
                .getId()
                .toString();
        authed(get("/api/v1/users/me/profile"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userCode").value(user.getUserCode()))
                .andExpect(jsonPath("$.school").value(nullValue()))
                .andExpect(jsonPath("$.github").value(nullValue()))
                .andExpect(jsonPath("$.version").value(0));

        authed(patch("/api/v1/users/me/profile"), """
                        {"version":0,"fullName":"  Nguyễn   Văn  An ","schoolId":"%s","bio":"Thích frontend.\\r\\nĐang học Spring."}
                        """.formatted(school))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.school.id").value(school))
                .andExpect(jsonPath("$.bio").value("Thích frontend.\nĐang học Spring."))
                .andExpect(jsonPath("$.version").value(1));

        // bio left out stays, school sent as null is removed
        authed(patch("/api/v1/users/me/profile"), "{\"version\":1,\"schoolId\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.school").value(nullValue()))
                .andExpect(jsonPath("$.bio").value("Thích frontend.\nĐang học Spring."))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn An"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getFullNameSearch())
                .isEqualTo("nguyen van an");
    }

    @Test
    @DisplayName("BR-USER-14: an outdated version is rejected")
    void updateMyProfile_shouldRejectOldVersion() throws Exception {
        authed(patch("/api/v1/users/me/profile"), "{\"version\":0,\"bio\":\"Lần một.\"}")
                .andExpect(status().isOk());

        authed(patch("/api/v1/users/me/profile"), "{\"version\":0,\"bio\":\"Tab cũ.\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_VERSION_CONFLICT"));
    }

    @Test
    @DisplayName("FR-USER-02: user ID, email and role cannot be changed by the user")
    void updateMyProfile_shouldRejectFieldsNotAllowed() throws Exception {
        authed(patch("/api/v1/users/me/profile"), """
                        {"version":0,"userCode":"HACK1","email":"x@gmail.com","fullName":"A","bio":"1\\n2\\n3\\n4\\n5\\n6"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'userCode')].code").value("NOT_ALLOWED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].code").value("NOT_ALLOWED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'fullName')].code").value("TOO_SHORT"))
                .andExpect(jsonPath("$.errors[?(@.field == 'bio')].code").value("TOO_LONG"));
    }

    @Test
    @DisplayName("BR-USER-03: a deactivated school cannot be picked")
    void updateMyProfile_shouldRejectInactiveSchool() throws Exception {
        School closed = School.create(
                "CLOSED" + SEQUENCE.incrementAndGet(), "Trường đã đóng " + System.nanoTime(), null, user.getId());
        closed.update(closed.getCode(), closed.getName(), null, false, user.getId());
        schoolRepository.save(closed);

        authed(patch("/api/v1/users/me/profile"), "{\"version\":0,\"schoolId\":\"%s\"}".formatted(closed.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("schoolId"))
                .andExpect(jsonPath("$.errors[0].code").value("NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("BR-USER-18, NFR-USER-07: public card has public fields only and hides inactive accounts")
    void getPublicProfile_shouldExposePublicFieldsOnly() throws Exception {
        authed(get("/api/v1/users/" + user.getId() + "/profile"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.userCode").doesNotExist())
                .andExpect(jsonPath("$.systemRole").doesNotExist());

        String pendingEmail = "pending" + System.nanoTime() + "@gmail.com";
        User pending = userRepository.save(User.createPending(
                pendingEmail, "Chưa Kích Hoạt", TestUserCodes.codeFor(pendingEmail), SystemRole.USER, null));
        authed(get("/api/v1/users/" + pending.getId() + "/profile"), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("API-USER-21: active schools with an ETag, 304 when unchanged")
    void listActiveSchools_shouldSupportEtag() throws Exception {
        String etag = authed(get("/api/v1/schools"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", greaterThanOrEqualTo(8)))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);

        authed(get("/api/v1/schools").header(HttpHeaders.IF_NONE_MATCH, etag), null)
                .andExpect(status().isNotModified());
    }

    private ResultActions authed(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    // A distinct client IP per login keeps the per-IP login limit (BR-AUTH-09) out of the way
    private static MockHttpServletRequestBuilder withIp(MockHttpServletRequestBuilder builder) {
        int n = SEQUENCE.incrementAndGet();
        String ip = "10.4." + (n / 250) + "." + (n % 250 + 1);
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }
}
