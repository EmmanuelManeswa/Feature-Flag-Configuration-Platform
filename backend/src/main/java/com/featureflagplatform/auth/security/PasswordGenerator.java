package com.featureflagplatform.auth.security;

import java.security.SecureRandom;

/**
 * Generates strong, random passwords for admin-created accounts —
 * {@link SecureRandom}, never {@code Math.random()} or any other
 * non-cryptographic source, matching the same "never a predictable source"
 * bar the deterministic-but-unpredictable-per-user rollout hash holds
 * itself to (see ADR-001) — different property, same underlying principle
 * of not substituting a weaker source where a strong one is called for.
 *
 * <p>The alphabet deliberately excludes visually ambiguous characters
 * (I/O/l/0/1) — this password is going to be read off a screen and either
 * typed or copy-pasted by a human, so a character a person could easily
 * misread is a real usability bug, not just a cosmetic one.
 */
public final class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%^&*-_=+";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final int LENGTH = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    /** A 16-character password guaranteed to contain at least one uppercase letter, one lowercase letter, one digit, and one symbol. */
    public static String generate() {
        char[] password = new char[LENGTH];
        password[0] = randomChar(UPPER);
        password[1] = randomChar(LOWER);
        password[2] = randomChar(DIGITS);
        password[3] = randomChar(SYMBOLS);
        for (int i = 4; i < LENGTH; i++) {
            password[i] = randomChar(ALL);
        }
        shuffle(password);
        return new String(password);
    }

    private static char randomChar(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }

    /** Fisher-Yates — without this, the first four characters would always be one-of-each-class in a fixed order, not a real shuffle. */
    private static void shuffle(char[] chars) {
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
    }
}
