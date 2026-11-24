package net.blazemc.login.config;

import net.blazemc.login.model.LanguageCode;

public record GeneralSettings(
        LanguageCode language,
        boolean debug,
        boolean useProxyForwardedAddress,
        int asyncThreads
) {
    public static GeneralSettings defaults() {
        return new GeneralSettings(LanguageCode.RO, false, true, 4);
    }
}
