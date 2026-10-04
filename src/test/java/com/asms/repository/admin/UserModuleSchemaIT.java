package com.asms.repository.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asms.TestcontainersConfiguration;
import com.asms.dto.common.ClientInfo;
import com.asms.entity.admin.AdminAuditAction;
import com.asms.entity.admin.AdminAuditLog;
import com.asms.entity.catalog.Holiday;
import com.asms.entity.catalog.HolidayKind;
import com.asms.entity.user.SystemRole;
import com.asms.entity.user.User;
import com.asms.entity.user.UserStatus;
import com.asms.repository.catalog.HolidayRepository;
import com.asms.repository.catalog.SchoolRepository;
import com.asms.repository.user.UserRepository;
import com.asms.service.admin.AdminAuditService;
import com.asms.service.admin.AdminAuditService.AuditEntry;
import com.asms.service.admin.AuditChanges;
import com.asms.support.TestUserCodes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Checks the Module 2 schema (V9-V12) against the entities, seed data and database-side rules. */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UserModuleSchemaIT {

    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.9", "JUnit");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private AdminAuditLogRepository auditLogRepository;

    @Autowired
    private AdminAuditService auditService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void users_shouldStoreSearchColumnAndSchool() {
        User user = newUser("search");
        user.changeFullName("Đỗ Thị Ánh");
        user.changeSchool(schoolRepository.findByActiveTrueOrderByNameAsc().getFirst());
        user.changeBio("Thích làm frontend.");
        userRepository.saveAndFlush(user);
        entityManager.clear();

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getFullNameSearch()).isEqualTo("do thi anh");
        assertThat(reloaded.getSchool()).isNotNull();
        assertThat(reloaded.getBio()).isEqualTo("Thích làm frontend.");
    }

    @Test
    void lockActiveAdmins_shouldReturnActiveAdminsOnly() {
        User admin = userRepository.saveAndFlush(User.createBootstrapAdmin(
                "lockadmin" + System.nanoTime() + "@gmail.com",
                "LA" + (System.nanoTime() % 100000),
                "Admin",
                "hash",
                Instant.now()));

        List<User> admins = userRepository.lockActiveAdmins();

        assertThat(admins).extracting(User::getId).contains(admin.getId());
        assertThat(admins).allSatisfy(u -> {
            assertThat(u.getSystemRole()).isEqualTo(SystemRole.ADMIN);
            assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
        });
    }

    @Test
    void lock_shouldBeStoredWithReasonAndAdmin() {
        User admin = userRepository.saveAndFlush(newUser("admin"));
        User user = newUser("locked");
        user.lock("Chia sẻ tài khoản cho người ngoài nhóm.", admin.getId(), Instant.now());

        userRepository.saveAndFlush(user);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getLockedBy())
                .isEqualTo(admin.getId());
    }

    @Test
    void seed_shouldProvideSchoolsAndHolidaysOf2026() {
        assertThat(schoolRepository.findByActiveTrueOrderByNameAsc()).hasSizeGreaterThanOrEqualTo(8);
        assertThat(schoolRepository.existsByCode("HCMUT")).isTrue();
        assertThat(holidayRepository.findByStartDateBetweenOrderByStartDateAsc(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .extracting(Holiday::getName)
                .contains("Tết Nguyên đán", "Quốc khánh");
    }

    @Test
    @DisplayName("BR-USER-21: two holidays cannot share a day")
    void holidays_shouldRejectOverlap() {
        User admin = userRepository.saveAndFlush(newUser("holiday"));
        Holiday overlapping = Holiday.create(
                "Trùng Tết",
                LocalDate.of(2026, 2, 20),
                LocalDate.of(2026, 2, 25),
                HolidayKind.OTHER,
                false,
                null,
                admin.getId());

        assertThatThrownBy(() -> holidayRepository.saveAndFlush(overlapping))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("NFR-USER-08: the audit row is written with its JSON changes and metadata")
    void auditService_shouldWriteRow() {
        User admin = userRepository.saveAndFlush(newUser("actor"));
        UUID target = UUID.randomUUID();

        auditService.record(
                AuditEntry.onUser(
                                admin.getId(),
                                AdminAuditAction.USER_LOCKED,
                                target,
                                AuditChanges.create().field("status", UserStatus.ACTIVE, UserStatus.LOCKED))
                        .withReason("Chia sẻ tài khoản cho người ngoài nhóm.")
                        .withMetadata(Map.of("revokedCount", 2)),
                CLIENT);
        entityManager.flush();

        Object changes = entityManager
                .createNativeQuery("select changes ->> 'status' from admin_audit_logs where target_id = :target")
                .setParameter("target", target)
                .getSingleResult();
        assertThat(changes.toString()).contains("\"from\"").contains("LOCKED");
        AdminAuditLog log = auditLogRepository.findAll().stream()
                .filter(row -> row.getTargetId().equals(target))
                .findFirst()
                .orElseThrow();
        assertThat(log.getMetadata()).contains("revokedCount");
        assertThat(log.getIpAddress()).isEqualTo("10.0.0.9");
    }

    @Test
    @DisplayName("NFR-USER-09: audit rows cannot be updated")
    void auditLogs_shouldBeAppendOnly() {
        User admin = userRepository.saveAndFlush(newUser("appendonly"));
        auditService.record(
                AuditEntry.onUser(admin.getId(), AdminAuditAction.USER_UPDATED, admin.getId(), AuditChanges.create()),
                CLIENT);
        entityManager.flush();

        assertThatThrownBy(() -> entityManager
                        .createNativeQuery("update admin_audit_logs set reason = 'x' where actor_id = :actor")
                        .setParameter("actor", admin.getId())
                        .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasStackTraceContaining("append-only");
    }

    private static User newUser(String prefix) {
        String email = prefix + System.nanoTime() + "@gmail.com";
        return User.createPending(email, "Test User", TestUserCodes.codeFor(email), SystemRole.USER, null);
    }
}
