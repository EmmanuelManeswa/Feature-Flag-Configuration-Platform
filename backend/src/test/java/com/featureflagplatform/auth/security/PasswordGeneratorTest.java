package com.featureflagplatform.auth.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    @Test
    void generatesA16CharacterPassword() {
        assertThat(PasswordGenerator.generate()).hasSize(16);
    }

    @Test
    void containsAtLeastOneCharacterFromEachClass() {
        String password = PasswordGenerator.generate();

        assertThat(password).matches(p -> p.chars().anyMatch(Character::isUpperCase));
        assertThat(password).matches(p -> p.chars().anyMatch(Character::isLowerCase));
        assertThat(password).matches(p -> p.chars().anyMatch(Character::isDigit));
        assertThat(password).matches(p -> p.chars().anyMatch(c -> "!@#$%^&*-_=+".indexOf(c) >= 0));
    }

    @Test
    void neverContainsVisuallyAmbiguousCharacters() {
        String password = PasswordGenerator.generate();
        assertThat(password).doesNotContainAnyWhitespaces();
        for (char ambiguous : new char[] {'I', 'O', 'l', '0', '1'}) {
            assertThat(password).doesNotContain(String.valueOf(ambiguous));
        }
    }

    @Test
    void generatesADifferentPasswordEveryTime() {
        Set<String> generated = new HashSet<>();
        IntStream.range(0, 100).forEach(i -> generated.add(PasswordGenerator.generate()));

        // A broken/predictable RNG (or a copy-paste bug that always returns
        // the same value) would collapse this set; a real SecureRandom-backed
        // 16-character generator collapsing 100 draws to fewer than 100
        // distinct values is not a realistic false-positive at this sample size.
        assertThat(generated).hasSize(100);
    }
}
