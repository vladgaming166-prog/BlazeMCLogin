package net.blazemc.login.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretYamlTest {

    @Test
    void fillsEmptyQuotedSecretAndKeepsIndent() {
        String yaml = """
                secrets:
                  ip-hmac: ""
                  session-hmac: ''
                """;
        String filled = SecretYaml.fill(yaml, "ip-hmac:", () -> "aaaabbbbccccdddd");
        assertThat(filled).contains("  ip-hmac: aaaabbbbccccdddd");
        assertThat(filled).contains("session-hmac: ''");
        String second = SecretYaml.fill(filled, "ip-hmac:", () -> "SHOULD_NOT_REPLACE");
        assertThat(second).isEqualTo(filled);
    }

    @Test
    void fillsChangeMeAndSharedSecret() {
        String yaml = "    shared-secret: CHANGE_ME\n";
        String filled = SecretYaml.fill(yaml, "shared-secret", () -> "deadbeef");
        assertThat(filled).isEqualTo("    shared-secret: deadbeef\n");
    }

    @Test
    void leavesUnrelatedKeysUntouched() {
        String yaml = "max-login-attempts: 5\nip-hmac: alreadyset\n";
        assertThat(SecretYaml.fill(yaml, "ip-hmac:", () -> "nope")).isEqualTo(yaml);
    }
}
