package com.fhwang.shortlinkops.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator shortCodeGenerator = new ShortCodeGenerator();

    @Test
    void generateReturnsAlphaNumericCodeWithExpectedLength() {
        String shortCode = shortCodeGenerator.generate();

        assertThat(shortCode)
                .hasSizeBetween(6, 8)
                .matches("^[a-zA-Z0-9]+$");
    }
}
