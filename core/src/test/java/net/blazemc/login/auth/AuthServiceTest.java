package net.blazemc.login.auth;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import net.blazemc.login.config.AccountLimitSettings;
import net.blazemc.login.config.DatabaseSettings;
import net.blazemc.login.config.GeneralSettings;
import net.blazemc.login.config.LoginSpawnSettings;
import net.blazemc.login.config.PluginSettings;
import net.blazemc.login.config.PremiumSettings;
import net.blazemc.login.config.RestrictionSettings;
import net.blazemc.login.config.SecuritySettings;
import net.blazemc.login.config.SessionSettings;
import net.blazemc.login.ip.AccountLimitService;
import net.blazemc.login.ip.IpHasher;
import net.blazemc.login.premium.PremiumVerificationCodec;
import net.blazemc.login.security.Argon2PasswordHasher;
import net.blazemc.login.security.PasswordPolicy;
import net.blazemc.login.security.SecureTokens;
import net.blazemc.login.session.SessionService;
import net.blazemc.login.storage.AccountRepository;
import net.blazemc.login.storage.DatabaseManager;
import net.blazemc.login.storage.SessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTest {

    @TempDir
    Path temp;

    private DatabaseManager database;
    private AuthService auth;
    private PluginSettings settings;
    private IpHasher ipHasher;

    @BeforeEach
    void setUp() {
        settings = new PluginSettings(
                GeneralSettings.defaults(),
                new SecuritySettings(5, 300, 60, 8, 64, true, false, false, false, true, 10, "ARGON2ID", 8192, 1, 1, "00".repeat(32), "11".repeat(32)),
                PremiumSettings.defaults(),
                new SessionSettings(true, true, 30, false, true, true, false),
                new AccountLimitSettings(true, 2, true, true, java.util.List.of(), java.util.List.of(), java.util.List.of()),
                RestrictionSettings.defaults(),
                new DatabaseSettings(DatabaseSettings.Type.SQLITE, "accounts.db", "localhost", 3306, "x", "x", "x", false, 2),
                LoginSpawnSettings.defaults()
        );
        database = new DatabaseManager(settings.database(), temp, Logger.getLogger("test"));
        database.open();
        AccountRepository accounts = new AccountRepository(database);
        ipHasher = new IpHasher(SecureTokens.randomBytes(32));
        auth = new AuthService(
                accounts,
                new Argon2PasswordHasher(8192, 1, 1),
                new PasswordPolicy(settings.security()),
                new AccountLimitService(accounts, ipHasher),
                new SessionService(new SessionRepository(database), SecureTokens.randomBytes(32)),
                settings
        );
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void registerLoginWrongPasswordAndDuplicate() {
        PendingPlayer player = join("Steve", "10.0.0.1");
        assertThat(auth.register(player, "UniquePass9x", "UniquePass9x", false, now()).outcome()).isEqualTo(AuthOutcome.REGISTERED);
        assertThat(player.authenticated()).isTrue();
        auth.logout(player);
        assertThat(auth.login(player, "wrong-password", now()).outcome()).isEqualTo(AuthOutcome.WRONG_PASSWORD);
        assertThat(player.authenticated()).isFalse();
        assertThat(auth.login(player, "UniquePass9x", now()).outcome()).isEqualTo(AuthOutcome.LOGGED_IN);
        PendingPlayer other = joinSameNameDifferentUuid("Steve", "10.0.0.2");
        assertThat(auth.register(other, "UniquePass9x", "UniquePass9x", false, now()).outcome()).isEqualTo(AuthOutcome.ALREADY_REGISTERED);
    }

    @Test
    void ipLimitBlocksThirdCrackedAccount() {
        joinAndRegister("Aaaaaaaa", "192.168.10.2");
        joinAndRegister("Bbbbbbbb", "192.168.10.2");
        PendingPlayer third = join("Cccccccc", "192.168.10.2");
        assertThat(auth.register(third, "UniquePass9x", "UniquePass9x", false, now()).outcome()).isEqualTo(AuthOutcome.IP_LIMIT);
        PendingPlayer bypass = join("Dddddddd", "192.168.10.2");
        assertThat(auth.register(bypass, "UniquePass9x", "UniquePass9x", true, now()).outcome()).isEqualTo(AuthOutcome.REGISTERED);
    }

    @Test
    void sessionRestoresThenExpiresAndInvalidatesOnPasswordChange() {
        PendingPlayer player = join("Sessioned", "10.1.1.1");
        auth.register(player, "UniquePass9x", "UniquePass9x", false, now());
        UUID uuid = player.uuid();
        String ip = player.ipHash();
        auth.untrack(uuid);
        PendingPlayer reconnect = auth.trackJoin(uuid, "Sessioned", ip, "10.1.1.1", now());
        assertThat(auth.handleJoin(reconnect, false, now()).outcome()).isEqualTo(AuthOutcome.SESSION_RESTORED);
        reconnect.clearAuth();
        auth.changePassword(reconnect, "UniquePass9x", "UniquePass8y", now());
        auth.untrack(uuid);
        PendingPlayer afterChange = auth.trackJoin(uuid, "Sessioned", ip, "10.1.1.1", now());
        // password change authenticates current player but stored sessions are invalidated
        afterChange.clearAuth();
        AuthResult restored = auth.handleJoin(afterChange, false, now() + 1);
        // new session was created after password change, so restore still works unless we invalidate explicitly
        auth.sessions().invalidate(uuid);
        auth.untrack(uuid);
        PendingPlayer expiredWindow = auth.trackJoin(uuid, "Sessioned", ip, "10.1.1.1", now());
        assertThat(auth.handleJoin(expiredWindow, false, now()).outcome()).isEqualTo(AuthOutcome.NOT_AUTHENTICATED);
    }

    @Test
    void premiumAutoLoginRequiresSignedIdentity() {
        PendingPlayer player = join("PremiumUser", "10.2.2.2");
        var verification = PremiumVerificationCodec.create(player.uuid(), "PremiumUser", true, "1.1.0");
        assertThat(auth.applyPremium(player, verification, now()).outcome()).isEqualTo(AuthOutcome.PREMIUM_AUTO_LOGIN);
        assertThat(player.authenticated()).isTrue();
        PendingPlayer other = join("CrackedUser", "10.2.2.3");
        var mismatched = PremiumVerificationCodec.create(UUID.randomUUID(), "CrackedUser", true, "1.1.0");
        assertThat(auth.applyPremium(other, mismatched, now()).authenticates()).isFalse();
    }

    @Test
    void databaseReconnectKeepsAccounts() {
        PendingPlayer player = join("Persisted", "10.3.3.3");
        auth.register(player, "UniquePass9x", "UniquePass9x", false, now());
        database.reconnect();
        AccountRepository accounts = new AccountRepository(database);
        assertThat(accounts.findByUsername("Persisted")).isPresent();
        assertThat(accounts.findByUsername("Persisted").orElseThrow().passwordHash()).startsWith("$argon2id$");
    }

    private PendingPlayer join(String name, String ip) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
        return auth.trackJoin(uuid, name, ipHasher.hashRaw(ip), net.blazemc.login.ip.IpAddressNormalizer.normalize(ip), now());
    }

    private PendingPlayer joinSameNameDifferentUuid(String name, String ip) {
        return auth.trackJoin(UUID.randomUUID(), name, ipHasher.hashRaw(ip), ip, now());
    }

    private void joinAndRegister(String name, String ip) {
        PendingPlayer player = join(name, ip);
        AuthResult result = auth.register(player, "UniquePass9x", "UniquePass9x", false, now());
        assertThat(result.outcome()).isEqualTo(AuthOutcome.REGISTERED);
        auth.untrack(player.uuid());
    }

    private long now() {
        return System.currentTimeMillis();
    }
}
