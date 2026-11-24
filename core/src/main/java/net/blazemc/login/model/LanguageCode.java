package net.blazemc.login.model;

import java.util.Locale;
import java.util.Optional;

public enum LanguageCode {
    RO("ro", "Română", "messages-ro.yml"),
    EN("en", "English", "messages-en.yml"),
    RO_ASCII("ro-ascii", "Romana (fara diacritice)", "messages-ro-ascii.yml");

    private final String code;
    private final String displayName;
    private final String resourceFile;

    LanguageCode(String code, String displayName, String resourceFile) {
        this.code = code;
        this.displayName = displayName;
        this.resourceFile = resourceFile;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public String resourceFile() {
        return resourceFile;
    }

    public static LanguageCode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return RO;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        normalized = normalized.replace("romanian", "ro").replace("english", "en");
        if ("ro-ascii".equals(normalized) || "roascii".equals(normalized) || "ro-no-diacritics".equals(normalized)
                || "romana".equals(normalized) || "romana-ascii".equals(normalized)) {
            return RO_ASCII;
        }
        for (LanguageCode code : values()) {
            if (code.code.equals(normalized)) {
                return code;
            }
        }
        if (normalized.startsWith("en")) {
            return EN;
        }
        return RO;
    }

    public static Optional<LanguageCode> tryParse(String raw) {
        LanguageCode parsed = parse(raw);
        if (raw == null) {
            return Optional.of(RO);
        }
        return Optional.of(parsed);
    }
}
