package net.blazemc.login.auth;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.blazemc.login.config.PluginSettings;
import net.blazemc.login.ip.AccountLimitService;
import net.blazemc.login.model.AccountStatus;
import net.blazemc.login.model.AccountType;
import net.blazemc.login.premium.PremiumVerification;
import net.blazemc.login.security.LoginAttemptTracker;
import net.blazemc.login.security.PasswordHasher;
import net.blazemc.login.security.PasswordPolicy;
import net.blazemc.login.security.RateLimiter;
import net.blazemc.login.session.SessionService;
import net.blazemc.login.storage.AccountRecord;
import net.blazemc.login.storage.AccountRepository;
import net.blazemc.login.storage.SessionRecord;

public final class AuthService {

    private final AccountRepository accounts;
    private final PasswordHasher hasher;
    private final PasswordPolicy policy;
    private final AccountLimitService limits;
    private final SessionService sessions;
    private final PluginSettings settings;
    private final RateLimiter rateLimiter;
    private final LoginAttemptTracker attempts;
    private final ConcurrentHashMap<UUID, PendingPlayer> online = new ConcurrentHashMap<>();

    public AuthService(
            AccountRepository accounts,
            PasswordHasher hasher,
            PasswordPolicy policy,
            AccountLimitService limits,
            SessionService sessions,
            PluginSettings settings
    ) {
        this.accounts = accounts;
        this.hasher = hasher;
        this.policy = policy;
        this.limits = limits;
        this.sessions = sessions;
        this.settings = settings;
        this.rateLimiter = new RateLimiter(settings.security().attemptsPerMinute());
        this.attempts = new LoginAttemptTracker(
                settings.security().maxLoginAttempts(),
                settings.security().lockDurationSeconds()
        );
    }

    public PendingPlayer trackJoin(UUID uuid, String username, String ipHash, String normalizedIp, long now) {
        PendingPlayer player = new PendingPlayer(uuid, username, ipHash, normalizedIp, now);
        PendingPlayer previous = online.put(uuid, player);
        if (previous != null && previous.authenticated()) {
            // Replace the slot but do not inherit authentication from a stale connection.
            previous.clearAuth();
        }
        return player;
    }

    public void untrack(UUID uuid) {
        online.remove(uuid);
    }

    public PendingPlayer pending(UUID uuid) {
        return online.get(uuid);
    }

    public boolean isAuthenticated(UUID uuid) {
        PendingPlayer pending = online.get(uuid);
        return pending != null && pending.authenticated();
    }

    public AuthResult handleJoin(PendingPlayer player, boolean bypassLogin, long now) {
        if (bypassLogin) {
            authenticate(player, AuthOutcome.BYPASS);
            return AuthResult.of(AuthOutcome.BYPASS);
        }
        Optional<AccountRecord> byUuid = accounts.findByUuid(player.uuid());
        Optional<AccountRecord> byName = accounts.findByUsername(player.username());
        AuthResult protectedName = protectReservedName(player, byUuid, byName);
        if (protectedName != null) {
            return protectedName;
        }
        if (byUuid.isPresent()) {
            AccountRecord account = byUuid.get();
            if (account.status() == AccountStatus.LOCKED) {
                return AuthResult.of(AuthOutcome.ACCOUNT_LOCKED, account);
            }
            if (isPremiumAccount(account)) {
                return AuthResult.of(AuthOutcome.NOT_AUTHENTICATED, account);
            }
            Optional<SessionRecord> session = sessions.restore(account, player.ipHash(), settings.sessions(), now);
            if (session.isPresent()) {
                accounts.save(account.withLastLogin(now, player.ipHash()).withUsername(player.username()));
                authenticate(player, AuthOutcome.SESSION_RESTORED);
                return AuthResult.of(AuthOutcome.SESSION_RESTORED, account);
            }
            return AuthResult.of(AuthOutcome.NOT_AUTHENTICATED, account);
        }
        return AuthResult.of(AuthOutcome.NOT_AUTHENTICATED, byName.orElse(null));
    }

    public AuthResult applyPremium(PendingPlayer player, PremiumVerification verification, long now) {
        if (!settings.premium().autoLoginEnabled()) {
            if (verification != null && verification.premium()) {
                player.markPremium(verification);
            }
            return AuthResult.of(AuthOutcome.NOT_AUTHENTICATED);
        }
        if (verification == null || !verification.premium()) {
            if (player != null) {
                player.markCrackedVerdict();
            }
            return AuthResult.of(AuthOutcome.NOT_AUTHENTICATED);
        }
        if (!verification.uuid().equals(player.uuid())) {
            return AuthResult.of(AuthOutcome.NOT_AUTHENTICATED);
        }
        if (settings.premium().requireVerifiedIdentity()
                && !verification.username().equalsIgnoreCase(player.username())) {
            return AuthResult.of(AuthOutcome.NOT_AUTHENTICATED);
        }
        player.markPremium(verification);
        Optional<AccountRecord> existing = accounts.findByUuid(player.uuid());
        Optional<AccountRecord> byName = accounts.findByUsername(player.username());
        if (byName.isPresent() && existing.isEmpty() && !byName.get().uuid().equals(player.uuid())) {
            if (isPremiumAccount(byName.get())) {
                return AuthResult.of(AuthOutcome.PREMIUM_NAME_PROTECTED, byName.get());
            }
        }
        AccountRecord account;
        if (existing.isPresent()) {
            account = existing.get().withType(AccountType.PREMIUM, verification.uuid())
                    .withUsername(player.username())
                    .withLastLogin(now, player.ipHash());
        } else {
            long time = now;
            account = new AccountRecord(
                    player.uuid(),
                    player.username(),
                    player.username().toLowerCase(Locale.ROOT),
                    AccountType.PREMIUM,
                    null,
                    null,
                    time,
                    time,
                    player.ipHash(),
                    AccountStatus.ACTIVE,
                    0,
                    verification.uuid()
            );
        }
        if (account.status() == AccountStatus.LOCKED) {
            return AuthResult.of(AuthOutcome.ACCOUNT_LOCKED, account);
        }
        accounts.save(account);
        sessions.create(account, player.ipHash(), settings.sessions(), now);
        if (!authenticate(player, AuthOutcome.PREMIUM_AUTO_LOGIN)) {
            return AuthResult.of(AuthOutcome.ALREADY_AUTHENTICATED, account);
        }
        return AuthResult.of(AuthOutcome.PREMIUM_AUTO_LOGIN, account);
    }

    public AuthResult register(PendingPlayer player, String password, String confirm, boolean bypassIp, long now) {
        if (!player.beginWork()) {
            return AuthResult.of(AuthOutcome.AUTH_IN_PROGRESS);
        }
        try {
            if (player.authenticated()) {
                return AuthResult.of(AuthOutcome.ALREADY_AUTHENTICATED);
            }
            if (player.premiumVerified() && settings.premium().disableRegistration()) {
                return AuthResult.of(AuthOutcome.PREMIUM_REGISTER_DISABLED);
            }
            if (!settings.cracked().enabled() || !settings.cracked().requireRegistration()) {
                if (!settings.cracked().enabled()) {
                    return AuthResult.of(AuthOutcome.CRACKED_DISABLED);
                }
            }
            Optional<AccountRecord> byUuid = accounts.findByUuid(player.uuid());
            Optional<AccountRecord> byName = accounts.findByUsername(player.username());
            AuthResult protectedName = protectReservedName(player, byUuid, byName);
            if (protectedName != null) {
                return protectedName;
            }
            if (byUuid.isPresent() || byName.isPresent()) {
                return AuthResult.of(AuthOutcome.ALREADY_REGISTERED, byUuid.or(() -> byName).orElse(null));
            }
            AuthOutcome passwordIssue = mapPassword(policy.validate(password, confirm));
            if (passwordIssue != null) {
                return AuthResult.of(passwordIssue);
            }
            AccountLimitService.Decision limit = limits.checkNewRegistration(
                    player.normalizedIp(), AccountType.CRACKED, settings.accountLimits(), bypassIp);
            if (limit == AccountLimitService.Decision.BLACKLISTED) {
                return AuthResult.of(AuthOutcome.IP_BLACKLISTED);
            }
            if (limit == AccountLimitService.Decision.LIMIT_REACHED) {
                return AuthResult.of(AuthOutcome.IP_LIMIT);
            }
            String hash = hasher.hash(password.toCharArray());
            AccountRecord account = new AccountRecord(
                    player.uuid(),
                    player.username(),
                    player.username().toLowerCase(Locale.ROOT),
                    player.premiumVerified() ? AccountType.PREMIUM : AccountType.CRACKED,
                    hash,
                    hasher.algorithmName(),
                    now,
                    now,
                    player.ipHash(),
                    AccountStatus.ACTIVE,
                    1,
                    player.premiumVerified() ? player.uuid() : null
            );
            accounts.save(account);
            sessions.create(account, player.ipHash(), settings.sessions(), now);
            attempts.clear(key(player));
            authenticate(player, AuthOutcome.REGISTERED);
            return AuthResult.of(AuthOutcome.REGISTERED, account);
        } finally {
            player.endWork();
        }
    }

    public AuthResult login(PendingPlayer player, String password, long now) {
        if (!player.beginWork()) {
            return AuthResult.of(AuthOutcome.AUTH_IN_PROGRESS);
        }
        try {
            if (player.authenticated()) {
                return AuthResult.of(AuthOutcome.ALREADY_AUTHENTICATED);
            }
            if (player.premiumVerified() && settings.premium().disablePasswordLogin()) {
                return AuthResult.of(AuthOutcome.PREMIUM_PASSWORD_DISABLED);
            }
            String key = key(player);
            if (settings.security().rateLimitEnabled() && !rateLimiter.allow(key, now)) {
                return AuthResult.of(AuthOutcome.RATE_LIMITED);
            }
            if (attempts.isLocked(key, now)) {
                return AuthResult.of(AuthOutcome.ACCOUNT_LOCKED);
            }
            Optional<AccountRecord> byUuid = accounts.findByUuid(player.uuid());
            Optional<AccountRecord> byName = accounts.findByUsername(player.username());
            AuthResult protectedName = protectReservedName(player, byUuid, byName);
            if (protectedName != null) {
                return protectedName;
            }
            if (byUuid.isEmpty()) {
                return AuthResult.of(AuthOutcome.NOT_REGISTERED);
            }
            AccountRecord account = byUuid.get();
            if (isPremiumAccount(account) && settings.premium().disablePasswordLogin()) {
                return AuthResult.of(AuthOutcome.PREMIUM_PASSWORD_DISABLED, account);
            }
            if (account.status() == AccountStatus.LOCKED) {
                return AuthResult.of(AuthOutcome.ACCOUNT_LOCKED, account);
            }
            if (!account.hasPassword()) {
                if (settings.premium().autoLoginEnabled() && player.premiumVerified() && settings.premium().bypassPassword()) {
                    return applyPremium(player, player.premium(), now);
                }
                return AuthResult.of(AuthOutcome.NOT_REGISTERED, account);
            }
            if (password == null || password.isEmpty()) {
                return AuthResult.of(AuthOutcome.PASSWORD_EMPTY);
            }
            boolean ok = hasher.verify(password.toCharArray(), account.passwordHash());
            if (!ok) {
                attempts.recordFailure(key, now);
                return AuthResult.of(AuthOutcome.WRONG_PASSWORD, account);
            }
            AccountRecord updated = account.withLastLogin(now, player.ipHash()).withUsername(player.username());
            if (hasher.needsRehash(account.passwordHash())) {
                updated = updated.withPassword(hasher.hash(password.toCharArray()), hasher.algorithmName(), account.passwordVersion());
            }
            accounts.save(updated);
            sessions.create(updated, player.ipHash(), settings.sessions(), now);
            attempts.clear(key);
            authenticate(player, AuthOutcome.LOGGED_IN);
            return AuthResult.of(AuthOutcome.LOGGED_IN, updated);
        } finally {
            player.endWork();
        }
    }

    public AuthResult logout(PendingPlayer player) {
        if (player == null || !player.authenticated()) {
            return AuthResult.of(AuthOutcome.NOT_AUTHENTICATED);
        }
        sessions.invalidate(player.uuid());
        player.clearAuth();
        return AuthResult.of(AuthOutcome.LOGGED_OUT);
    }

    public AuthResult changePassword(PendingPlayer player, String oldPassword, String newPassword, long now) {
        if (player == null || !player.authenticated()) {
            return AuthResult.of(AuthOutcome.NOT_AUTHENTICATED);
        }
        if (player.premiumVerified() && settings.premium().disablePasswordLogin()) {
            return AuthResult.of(AuthOutcome.PREMIUM_PASSWORD_DISABLED);
        }
        Optional<AccountRecord> existing = accounts.findByUuid(player.uuid());
        if (existing.isEmpty()) {
            return AuthResult.of(AuthOutcome.NOT_REGISTERED);
        }
        AccountRecord account = existing.get();
        if (isPremiumAccount(account) && settings.premium().disablePasswordLogin()) {
            return AuthResult.of(AuthOutcome.PREMIUM_PASSWORD_DISABLED, account);
        }
        if (account.hasPassword() && (oldPassword == null || !hasher.verify(oldPassword.toCharArray(), account.passwordHash()))) {
            return AuthResult.of(AuthOutcome.WRONG_PASSWORD, account);
        }
        AuthOutcome passwordIssue = mapPassword(policy.validate(newPassword, newPassword));
        if (passwordIssue != null) {
            return AuthResult.of(passwordIssue);
        }
        int version = account.passwordVersion() + 1;
        AccountRecord updated = account.withPassword(hasher.hash(newPassword.toCharArray()), hasher.algorithmName(), version)
                .withLastLogin(now, player.ipHash());
        accounts.save(updated);
        if (settings.sessions().invalidateOnPasswordChange()) {
            sessions.invalidate(player.uuid());
        }
        sessions.create(updated, player.ipHash(), settings.sessions(), now);
        return AuthResult.of(AuthOutcome.PASSWORD_CHANGED, updated);
    }

    public AuthResult unregister(UUID uuid) {
        sessions.invalidate(uuid);
        PendingPlayer pending = online.get(uuid);
        if (pending != null) {
            pending.clearAuth();
        }
        return accounts.delete(uuid) ? AuthResult.of(AuthOutcome.LOGGED_OUT) : AuthResult.of(AuthOutcome.NOT_REGISTERED);
    }

    public AuthResult resetPassword(UUID uuid) {
        Optional<AccountRecord> existing = accounts.findByUuid(uuid);
        if (existing.isEmpty()) {
            return AuthResult.of(AuthOutcome.NOT_REGISTERED);
        }
        AccountRecord updated = existing.get().withPassword(null, null, existing.get().passwordVersion() + 1)
                .withStatus(AccountStatus.RESET_REQUIRED);
        accounts.save(updated);
        sessions.invalidate(uuid);
        PendingPlayer pending = online.get(uuid);
        if (pending != null) {
            pending.clearAuth();
        }
        return AuthResult.of(AuthOutcome.PASSWORD_CHANGED, updated);
    }

    public AuthResult setLocked(UUID uuid, boolean locked) {
        Optional<AccountRecord> existing = accounts.findByUuid(uuid);
        if (existing.isEmpty()) {
            return AuthResult.of(AuthOutcome.NOT_REGISTERED);
        }
        AccountRecord updated = existing.get().withStatus(locked ? AccountStatus.LOCKED : AccountStatus.ACTIVE);
        accounts.save(updated);
        if (locked) {
            sessions.invalidate(uuid);
            PendingPlayer pending = online.get(uuid);
            if (pending != null) {
                pending.clearAuth();
            }
        }
        return AuthResult.of(locked ? AuthOutcome.ACCOUNT_LOCKED : AuthOutcome.LOGGED_IN, updated);
    }

    public Optional<AccountRecord> find(String nameOrUuid) {
        try {
            return accounts.findByUuid(UUID.fromString(nameOrUuid));
        } catch (RuntimeException ignored) {
            return accounts.findByUsername(nameOrUuid);
        }
    }

    public AccountRepository accounts() {
        return accounts;
    }

    public SessionService sessions() {
        return sessions;
    }

    public PluginSettings settings() {
        return settings;
    }

    public AccountLimitService limits() {
        return limits;
    }

    private AuthResult protectReservedName(
            PendingPlayer player,
            Optional<AccountRecord> byUuid,
            Optional<AccountRecord> byName
    ) {
        if (!settings.premium().protectReservedNames()) {
            return null;
        }
        if (player.premiumVerified()) {
            return null;
        }
        if (byName.isPresent() && isPremiumAccount(byName.get()) && !byName.get().uuid().equals(player.uuid())) {
            return AuthResult.of(AuthOutcome.PREMIUM_NAME_PROTECTED, byName.get());
        }
        if (byUuid.isPresent() && isPremiumAccount(byUuid.get()) && !player.premiumVerified()) {
            return AuthResult.of(AuthOutcome.PREMIUM_NAME_PROTECTED, byUuid.get());
        }
        return null;
    }

    private static boolean isPremiumAccount(AccountRecord account) {
        return account.accountType() == AccountType.PREMIUM || account.premiumUuid() != null;
    }

    private boolean authenticate(PendingPlayer player, AuthOutcome source) {
        return player.tryMarkAuthenticated(source);
    }

    private String key(PendingPlayer player) {
        return player.uuid() + "|" + player.ipHash();
    }

    private AuthOutcome mapPassword(PasswordPolicy.Failure failure) {
        if (failure == null) {
            return null;
        }
        return switch (failure) {
            case EMPTY -> AuthOutcome.PASSWORD_EMPTY;
            case TOO_SHORT -> AuthOutcome.PASSWORD_TOO_SHORT;
            case TOO_LONG -> AuthOutcome.PASSWORD_TOO_LONG;
            case TOO_COMMON -> AuthOutcome.PASSWORD_TOO_COMMON;
            case COMPLEXITY -> AuthOutcome.PASSWORD_COMPLEXITY;
            case MISMATCH -> AuthOutcome.PASSWORD_MISMATCH;
        };
    }
}
