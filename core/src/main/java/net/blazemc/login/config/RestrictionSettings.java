package net.blazemc.login.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record RestrictionSettings(
        boolean enabled,
        boolean freezePlayer,
        boolean disableInventory,
        boolean disableChat,
        boolean disableDamage,
        boolean disableInteractions,
        boolean teleportToLoginSpawn,
        boolean disableMove,
        boolean disableDrop,
        boolean disablePickup,
        boolean disableCommands,
        List<String> allowedCommands
) {
    public static RestrictionSettings defaults() {
        return new RestrictionSettings(
                true, true, true, true, true, true, false,
                true, true, true, true,
                List.of("/login", "/register", "/l", "/reg", "/log")
        );
    }

    public Set<String> allowedCommandRoots() {
        return allowedCommands.stream()
                .map(RestrictionSettings::normalizeCommand)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static String normalizeCommand(String command) {
        String value = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        int space = value.indexOf(' ');
        if (space >= 0) {
            value = value.substring(0, space);
        }
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        return value;
    }
}
