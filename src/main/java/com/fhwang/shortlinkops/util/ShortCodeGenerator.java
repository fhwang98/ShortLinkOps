package com.fhwang.shortlinkops.util;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {

    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int MIN_LENGTH = 6;
    private static final int MAX_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        int length = MIN_LENGTH + random.nextInt(MAX_LENGTH - MIN_LENGTH + 1);
        StringBuilder shortCode = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            shortCode.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }

        return shortCode.toString();
    }
}
