package net.blazemc.login.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.blazemc.login.config.YamlSupport;

/**
 * Resolves the HMAC key used by the Velocity ↔ Paper bridge without requiring
 * a second secret to be copied by hand. When {@code premium.yml} leaves
 * {@code shared-secret} empty, both sides use the Velocity modern-forwarding
 * secret that the network already shares.
 */
public final class ForwardingSecretLocator {

    private static final Pattern TOML_SECRET_FILE = Pattern.compile("secret-file\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern TOML_SECRET = Pattern.compile("forwarding-secret\\s*=\\s*\"([^\"]+)\"");

    private ForwardingSecretLocator() {
    }

    public static byte[] resolve(String configuredHex, Path pluginDataFolder, Path serverRoot) {
        byte[] configured = PremiumVerifierParse.parse(configuredHex);
        if (configured.length > 0) {
            return configured;
        }
        Optional<String> forwarding = findForwardingSecret(serverRoot);
        if (forwarding.isEmpty() && pluginDataFolder != null) {
            Path parent = pluginDataFolder.getParent();
            if (parent != null && parent.getParent() != null) {
                forwarding = findForwardingSecret(parent.getParent());
            }
        }
        return forwarding.map(value -> value.getBytes(StandardCharsets.UTF_8)).orElse(new byte[0]);
    }

    public static Optional<String> findForwardingSecret(Path serverRoot) {
        if (serverRoot == null || !Files.isDirectory(serverRoot)) {
            return Optional.empty();
        }
        Path forwardingFile = serverRoot.resolve("forwarding.secret");
        Optional<String> fromFile = readSingleLine(forwardingFile);
        if (fromFile.isPresent()) {
            return fromFile;
        }
        Path velocityToml = serverRoot.resolve("velocity.toml");
        if (Files.isRegularFile(velocityToml)) {
            try {
                String toml = Files.readString(velocityToml, StandardCharsets.UTF_8);
                Matcher fileMatcher = TOML_SECRET_FILE.matcher(toml);
                if (fileMatcher.find()) {
                    Optional<String> named = readSingleLine(serverRoot.resolve(fileMatcher.group(1)));
                    if (named.isPresent()) {
                        return named;
                    }
                }
                Matcher inline = TOML_SECRET.matcher(toml);
                if (inline.find()) {
                    String value = inline.group(1).trim();
                    if (!value.isEmpty()) {
                        return Optional.of(value);
                    }
                }
            } catch (IOException ignored) {
                // fall through to Paper config
            }
        }
        return readPaperVelocitySecret(serverRoot.resolve("config").resolve("paper-global.yml"));
    }

    static Optional<String> readPaperVelocitySecret(Path paperGlobal) {
        if (!Files.isRegularFile(paperGlobal)) {
            return Optional.empty();
        }
        var yaml = YamlSupport.load(paperGlobal);
        String secret = YamlSupport.string(yaml, "proxies.velocity.secret", "");
        if (secret == null || secret.isBlank() || "CHANGE_ME".equalsIgnoreCase(secret)) {
            return Optional.empty();
        }
        return Optional.of(secret.trim());
    }

    private static Optional<String> readSingleLine(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return Optional.empty();
            }
            int newline = raw.indexOf('\n');
            String first = newline >= 0 ? raw.substring(0, newline).trim() : raw;
            return first.isEmpty() ? Optional.empty() : Optional.of(first);
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    /**
     * Local helper so this class does not depend on plugin-layer HexFormat parsing.
     */
    static final class PremiumVerifierParse {
        private PremiumVerifierParse() {
        }

        static byte[] parse(String hex) {
            return net.blazemc.login.premium.PremiumVerifier.parseSecret(hex);
        }
    }
}
