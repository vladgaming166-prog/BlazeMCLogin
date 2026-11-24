package net.blazemc.login.ip;

import java.util.List;
import java.util.Locale;
import net.blazemc.login.config.AccountLimitSettings;
import net.blazemc.login.model.AccountType;
import net.blazemc.login.storage.AccountRecord;
import net.blazemc.login.storage.AccountRepository;

public final class AccountLimitService {

    public enum Decision {
        ALLOW,
        LIMIT_REACHED,
        BLACKLISTED,
        WHITELISTED
    }

    private final AccountRepository accounts;
    private final IpHasher hasher;

    public AccountLimitService(AccountRepository accounts, IpHasher hasher) {
        this.accounts = accounts;
        this.hasher = hasher;
    }

    public Decision checkNewRegistration(String normalizedIp, AccountType type, AccountLimitSettings settings, boolean bypass) {
        if (normalizedIp == null || normalizedIp.isBlank()) {
            return Decision.ALLOW;
        }
        String hash = hasher.hashNormalized(normalizedIp);
        if (isListed(settings.blacklist(), normalizedIp, hash)) {
            return Decision.BLACKLISTED;
        }
        if (isListed(settings.whitelist(), normalizedIp, hash)) {
            return Decision.WHITELISTED;
        }
        if (bypass || settings.unlimited()) {
            return Decision.ALLOW;
        }
        boolean countThis = (type == AccountType.PREMIUM && settings.countPremiumAccounts())
                || (type != AccountType.PREMIUM && settings.countCrackedAccounts());
        if (!countThis) {
            return Decision.ALLOW;
        }
        int existing = accounts.countByIpHash(hash, settings.countPremiumAccounts(), settings.countCrackedAccounts());
        if (existing >= settings.maxAccountsPerIp()) {
            return Decision.LIMIT_REACHED;
        }
        return Decision.ALLOW;
    }

    public List<AccountRecord> accountsForIp(String rawIp) {
        String normalized = IpAddressNormalizer.normalize(rawIp);
        return accounts.findByIpHash(hasher.hashNormalized(normalized));
    }

    private boolean isListed(List<String> list, String normalizedIp, String hash) {
        if (list == null) {
            return false;
        }
        for (String entry : list) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String value = entry.trim();
            if (value.equalsIgnoreCase(hash)) {
                return true;
            }
            try {
                String normalized = IpAddressNormalizer.normalize(value);
                if (normalized.equals(normalizedIp) || hasher.hashNormalized(normalized).equals(hash)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                if (value.toLowerCase(Locale.ROOT).equals(normalizedIp)) {
                    return true;
                }
            }
        }
        return false;
    }
}
