package net.blazemc.login.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ForwardingSecretLocatorTest {

    @TempDir
    Path temp;

    @Test
    void configuredHexWins() {
        byte[] resolved = ForwardingSecretLocator.resolve("aabbccdd", temp, temp);
        assertThat(resolved).isEqualTo(java.util.HexFormat.of().parseHex("aabbccdd"));
    }

    @Test
    void emptyConfigUsesForwardingSecretFile() throws Exception {
        Files.writeString(temp.resolve("forwarding.secret"), "proxy-secret-value\n", StandardCharsets.UTF_8);
        byte[] resolved = ForwardingSecretLocator.resolve("", temp.resolve("plugins").resolve("BlazeMCLogin"), temp);
        assertThat(new String(resolved, StandardCharsets.UTF_8)).isEqualTo("proxy-secret-value");
    }

    @Test
    void paperGlobalVelocitySecretIsUsed() throws Exception {
        Path config = temp.resolve("config");
        Files.createDirectories(config);
        Files.writeString(config.resolve("paper-global.yml"), """
                proxies:
                  velocity:
                    enabled: true
                    secret: paper-forwarding
                """, StandardCharsets.UTF_8);
        byte[] resolved = ForwardingSecretLocator.resolve("", temp.resolve("plugins").resolve("BlazeMCLogin"), temp);
        assertThat(new String(resolved, StandardCharsets.UTF_8)).isEqualTo("paper-forwarding");
    }
}
