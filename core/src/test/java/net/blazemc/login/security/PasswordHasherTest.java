package net.blazemc.login.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher(8192, 1, 1);

    @Test
    void hashesAndVerifiesWithoutStoringPlaintext() {
        String hash = hasher.hash("correct horse".toCharArray());
        assertThat(hash).startsWith("$argon2id$");
        assertThat(hash).doesNotContain("correct horse");
        assertThat(hasher.verify("correct horse".toCharArray(), hash)).isTrue();
        assertThat(hasher.verify("wrong password".toCharArray(), hash)).isFalse();
        assertThat(hasher.verify("correct horse".toCharArray(), "$garbage$")).isFalse();
    }

    @Test
    void policyRejectsEmptyCommonAndMismatchedPasswords() {
        PasswordPolicy policy = new PasswordPolicy(net.blazemc.login.config.SecuritySettings.defaults());
        assertThat(policy.validate("", "")).isEqualTo(PasswordPolicy.Failure.EMPTY);
        assertThat(policy.validate("short", "short")).isEqualTo(PasswordPolicy.Failure.TOO_SHORT);
        assertThat(policy.validate("password123", "password123")).isEqualTo(PasswordPolicy.Failure.TOO_COMMON);
        assertThat(policy.validate("UniquePass9x", "other")).isEqualTo(PasswordPolicy.Failure.MISMATCH);
        assertThat(policy.validate("UniquePass9x", "UniquePass9x")).isNull();
    }
}
