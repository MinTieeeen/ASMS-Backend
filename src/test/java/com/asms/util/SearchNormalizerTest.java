package com.asms.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SearchNormalizerTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Nguyễn Văn A|nguyen van a",
                "  ĐẶNG   thị  Ánh |dang thi anh",
                "Trường Đại học Bách khoa, ĐHQG-HCM|truong dai hoc bach khoa, dhqg-hcm",
                "Lê Hoàng Yến|le hoang yen"
            })
    void toSearchKey_shouldDropAccentsAndCase(String input, String expected) {
        assertThat(SearchNormalizer.toSearchKey(input)).isEqualTo(expected);
    }

    @Test
    void collapseWhitespace_shouldTrimAndTurnBlankIntoNull() {
        assertThat(SearchNormalizer.collapseWhitespace("  Trần \t Thị\nB ")).isEqualTo("Trần Thị B");
        assertThat(SearchNormalizer.collapseWhitespace("   ")).isNull();
        assertThat(SearchNormalizer.collapseWhitespace(null)).isNull();
    }

    @Test
    void escapeLike_shouldMakeWildcardsLiteral() {
        assertThat(SearchNormalizer.escapeLike("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
    }
}
