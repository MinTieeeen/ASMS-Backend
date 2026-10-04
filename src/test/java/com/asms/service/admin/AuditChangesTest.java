package com.asms.service.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.asms.entity.user.UserStatus;
import org.junit.jupiter.api.Test;

class AuditChangesTest {

    @Test
    void field_shouldKeepOnlyRealChangesAndStoreEnumsByName() {
        AuditChanges changes = AuditChanges.create()
                .field("fullName", "Nguyễn Văn A", "Nguyễn Văn A")
                .field("status", UserStatus.ACTIVE, UserStatus.LOCKED)
                .field("schoolId", null, null);

        assertThat(changes.asMap()).containsOnlyKeys("status");
        assertThat(changes.asMap().get("status")).isEqualTo(new AuditChanges.Change("ACTIVE", "LOCKED"));
    }

    @Test
    void isEmpty_shouldBeTrueWhenNothingChanged() {
        assertThat(AuditChanges.create().field("bio", "a", "a").isEmpty()).isTrue();
    }
}
