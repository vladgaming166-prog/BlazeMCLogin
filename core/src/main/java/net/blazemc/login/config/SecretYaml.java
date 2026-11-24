package net.blazemc.login.config;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Fills empty or placeholder YAML secret keys in place without logging values.
 */
public final class SecretYaml {

    private SecretYaml() {
    }

    public static String fill(String yaml, String key, Supplier<String> secretFactory) {
        if (yaml == null || yaml.isEmpty() || key == null || key.isBlank()) {
            return yaml == null ? "" : yaml;
        }
        String needle = key.trim();
        if (!needle.endsWith(":")) {
            needle = needle + ":";
        }
        String[] lines = yaml.split("\n", -1);
        boolean changed = false;
        StringBuilder out = new StringBuilder(yaml.length() + 64);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith(needle)) {
                String value = trimmed.substring(needle.length()).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                if (needsSecret(value)) {
                    int idx = line.indexOf(trimmed);
                    String indent = idx >= 0 ? line.substring(0, idx) : "";
                    out.append(indent).append(needle).append(' ').append(secretFactory.get());
                    changed = true;
                } else {
                    out.append(line);
                }
            } else {
                out.append(line);
            }
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return changed ? out.toString() : yaml;
    }

    public static boolean needsSecret(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "\"\"".equals(trimmed) || "''".equals(trimmed)) {
            return true;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return "CHANGE_ME".equals(upper) || "CHANGEME".equals(upper) || "TODO".equals(upper);
    }
}
