package net.blazemc.login.storage;

import java.util.UUID;
import net.blazemc.login.model.AccountStatus;
import net.blazemc.login.model.AccountType;

public record AccountRecord(
        UUID uuid,
        String username,
        String usernameLower,
        AccountType accountType,
        String passwordHash,
        String hashAlgorithm,
        long registeredAt,
        long lastLoginAt,
        String ipHash,
        AccountStatus status,
        int passwordVersion,
        UUID premiumUuid
) {
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public AccountRecord withPassword(String hash, String algorithm, int version) {
        return new AccountRecord(uuid, username, usernameLower, accountType, hash, algorithm,
                registeredAt, lastLoginAt, ipHash, status, version, premiumUuid);
    }

    public AccountRecord withLastLogin(long time, String newIpHash) {
        return new AccountRecord(uuid, username, usernameLower, accountType, passwordHash, hashAlgorithm,
                registeredAt, time, newIpHash, status, passwordVersion, premiumUuid);
    }

    public AccountRecord withStatus(AccountStatus newStatus) {
        return new AccountRecord(uuid, username, usernameLower, accountType, passwordHash, hashAlgorithm,
                registeredAt, lastLoginAt, ipHash, newStatus, passwordVersion, premiumUuid);
    }

    public AccountRecord withType(AccountType type, UUID premium) {
        return new AccountRecord(uuid, username, usernameLower, type, passwordHash, hashAlgorithm,
                registeredAt, lastLoginAt, ipHash, status, passwordVersion, premium);
    }

    public AccountRecord withUsername(String name) {
        return new AccountRecord(uuid, name, name.toLowerCase(), accountType, passwordHash, hashAlgorithm,
                registeredAt, lastLoginAt, ipHash, status, passwordVersion, premiumUuid);
    }

    public AccountRecord withUsernameClaim(String usernameLower) {
        return new AccountRecord(uuid, username, usernameLower, accountType, passwordHash, hashAlgorithm,
                registeredAt, lastLoginAt, ipHash, status, passwordVersion, premiumUuid);
    }
}
