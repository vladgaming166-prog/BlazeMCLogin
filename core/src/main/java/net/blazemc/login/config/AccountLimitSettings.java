package net.blazemc.login.config;

import java.util.List;

public record AccountLimitSettings(
        boolean enabled,
        int maxAccountsPerIp,
        boolean countPremiumAccounts,
        boolean countCrackedAccounts,
        List<String> exemptPermissions,
        List<String> whitelist,
        List<String> blacklist
) {
    public static AccountLimitSettings defaults() {
        return new AccountLimitSettings(
                true, 3, true, true,
                List.of("blazemclogin.bypass.iplimit"),
                List.of(),
                List.of()
        );
    }

    public boolean unlimited() {
        return !enabled || maxAccountsPerIp <= 0;
    }
}
