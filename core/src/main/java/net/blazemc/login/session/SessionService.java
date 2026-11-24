package net.blazemc.login.session;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import net.blazemc.login.config.SessionSettings;
import net.blazemc.login.security.HmacSha256;
import net.blazemc.login.security.SecureTokens;
import net.blazemc.login.storage.AccountRecord;
import net.blazemc.login.storage.SessionRecord;
import net.blazemc.login.storage.SessionRepository;

public final class SessionService {

    private final SessionRepository repository;
    private final byte[] tokenSecret;

    public SessionService(SessionRepository repository, byte[] tokenSecret) {
        this.repository = repository;
        this.tokenSecret = tokenSecret.clone();
    }

    public SessionRecord create(AccountRecord account, String ipHash, SessionSettings settings, long now) {
        if (!settings.enabled()) {
            return null;
        }
        byte[] token = SecureTokens.randomBytes(32);
        String hash = HexFormat.of().formatHex(HmacSha256.sign(tokenSecret, token));
        long expires = now + Math.max(1, settings.expirationMinutes()) * 60_000L;
        SessionRecord record = new SessionRecord(hash, account.uuid(), now, expires, ipHash, account.passwordVersion());
        if (settings.preventMultipleSessions()) {
            repository.deleteByUuid(account.uuid());
        }
        repository.save(record);
        return record;
    }

    public Optional<SessionRecord> restore(AccountRecord account, String ipHash, SessionSettings settings, long now) {
        if (!settings.enabled() || !settings.rememberAfterReconnect()) {
            return Optional.empty();
        }
        repository.deleteExpired(now);
        Optional<SessionRecord> found = repository.findLatest(account.uuid(), now);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        SessionRecord session = found.get();
        if (session.expired(now)) {
            return Optional.empty();
        }
        if (settings.invalidateOnPasswordChange() && session.passwordVersion() != account.passwordVersion()) {
            repository.deleteByUuid(account.uuid());
            return Optional.empty();
        }
        if ((settings.bindToIp() || settings.invalidateOnIpChange())
                && ipHash != null
                && session.ipHash() != null
                && !session.ipHash().equals(ipHash)) {
            repository.deleteByUuid(account.uuid());
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void invalidate(UUID uuid) {
        repository.deleteByUuid(uuid);
    }

    public static byte[] secretFromHex(String hex) {
        if (hex == null || hex.isBlank()) {
            return "blazemclogin-session-fallback".getBytes(StandardCharsets.UTF_8);
        }
        try {
            return HexFormat.of().parseHex(hex.trim());
        } catch (RuntimeException ex) {
            return hex.getBytes(StandardCharsets.UTF_8);
        }
    }
}
