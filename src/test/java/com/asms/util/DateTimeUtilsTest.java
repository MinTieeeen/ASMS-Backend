package com.asms.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateTimeUtilsTest {

    @Test
    void endOfDay_shouldBe2359InVietnamTime_whenUsingDefaultZone() {
        Instant result = DateTimeUtils.endOfDay(LocalDate.of(2026, 10, 1));

        // 23:59 GMT+7 = 16:59 UTC
        assertThat(result).isEqualTo(Instant.parse("2026-10-01T16:59:00Z"));
    }
}
