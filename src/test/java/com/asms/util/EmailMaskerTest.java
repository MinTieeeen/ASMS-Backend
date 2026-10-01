package com.asms.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EmailMaskerTest {

    @ParameterizedTest
    @CsvSource({
        "nguyen@gmail.com, ng***@gmail.com",
        "ab@gmail.com, a***@gmail.com",
        "a@gmail.com, a***@gmail.com",
        "not-an-email, ***",
    })
    void mask_shouldHideTheLocalPart(String email, String expected) {
        assertThat(EmailMasker.mask(email)).isEqualTo(expected);
    }
}
