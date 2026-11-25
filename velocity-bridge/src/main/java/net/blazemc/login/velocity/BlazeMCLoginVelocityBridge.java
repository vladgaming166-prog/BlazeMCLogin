package net.blazemc.login.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.blazemc.login.PluginConstants;
import net.blazemc.login.config.YamlSupport;
import net.blazemc.login.premium.MinecraftProfileLookup;
import net.blazemc.login.premium.MojangProfileClient;
import net.blazemc.login.premium.PremiumVerification;
import net.blazemc.login.premium.PremiumVerificationCodec;
import net.blazemc.login.premium.PremiumVerifier;
import net.blazemc.login.security.ForwardingSecretLocator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.LoggerFactory;

@Plugin(
        id = "blazemclogin-bridge",
        name = "BlazeMCLogin Velocity Bridge",
        version = "1.1.0",
        authors = {"BlazeMC Network"},
        description = "Premium auto-auth, reserved-name protection, and HMAC bridge for BlazeMCLogin"
)
public final class BlazeMCLoginVelocityBridge {

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(PluginConstants.CHANNEL_ID);

    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final org.slf4j.Logger logger;
    private final MinecraftProfileLookup profiles;
    private byte[] secret = new byte[0];
    private boolean enabled = true;
    private boolean protectReservedNames = true;
    private boolean rejectUnverified = true;
    private boolean skipLimboForPremium = true;

    @Inject
    public BlazeMCLoginVelocityBridge(ProxyServer proxy, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        this.logger = LoggerFactory.getLogger("blazemclogin-bridge");
        this.profiles = new MojangProfileClient();
    }

    BlazeMCLoginVelocityBridge(ProxyServer proxy, Path dataDirectory, MinecraftProfileLookup profiles) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        this.logger = LoggerFactory.getLogger("blazemclogin-bridge");
        this.profiles = profiles;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        loadConfig();
        proxy.getChannelRegistrar().register(CHANNEL);
        boolean detector = proxy.getPluginManager().getPlugin("blazemc-connection-detector").isPresent();
        logger.info("BlazeMCLogin Velocity Bridge enabled. Connection Detector: {}", detector ? "detected" : "not loaded");
        if (secret.length == 0) {
            logger.warn("No HMAC secret resolved. Set bridge.shared-secret or rely on Velocity forwarding.secret. Premium packets will not be sent. The value is not logged.");
        }
        LimboSkipHook.register(proxy, this, logger);
    }

    @Subscribe
    public EventTask onPreLogin(PreLoginEvent event) {
        if (!protectReservedNames) {
            return null;
        }
        if (!event.getResult().isAllowed()) {
            return null;
        }
        String username = event.getUsername();
        return EventTask.async(() -> {
            MinecraftProfileLookup.ProfileStatus status = profiles.lookup(username);
            if (status == MinecraftProfileLookup.ProfileStatus.UNAVAILABLE) {
                if (rejectUnverified) {
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(unavailableMessage()));
                }
                return;
            }
            if (status == MinecraftProfileLookup.ProfileStatus.PREMIUM) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
            }
        });
    }

    @Subscribe
    public void onPostConnect(ServerPostConnectEvent event) {
        sendVerification(event.getPlayer(), 0);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        // No session state is kept in the bridge.
    }

    boolean skipLimboForPremium() {
        return skipLimboForPremium;
    }

    ProxyServer proxy() {
        return proxy;
    }

    private void sendVerification(Player player, int attempt) {
        if (!enabled || secret.length == 0) {
            return;
        }
        long delay = attempt == 0 ? 50L : 200L * attempt;
        proxy.getScheduler().buildTask(this, () -> {
            if (!player.isActive()) {
                return;
            }
            boolean premium = player.isOnlineMode();
            Optional<com.velocitypowered.api.proxy.ServerConnection> connection = player.getCurrentServer();
            if (connection.isEmpty()) {
                if (attempt < 6) {
                    sendVerification(player, attempt + 1);
                }
                return;
            }
            confirmOnlineMode(player);
            PremiumVerification verification = PremiumVerificationCodec.create(
                    player.getUniqueId(),
                    player.getUsername(),
                    premium,
                    detectorVersion()
            );
            byte[] packet = PremiumVerificationCodec.encode(verification, secret);
            boolean sent = connection.get().sendPluginMessage(CHANNEL, packet);
            if (!sent && attempt < 6) {
                sendVerification(player, attempt + 1);
            }
        }).delay(delay, TimeUnit.MILLISECONDS).schedule();
    }

    private void confirmOnlineMode(Player player) {
        try {
            // Velocity 3.5+ LoginEvent.getServerIdHash is not available here; isOnlineMode()
            // is the post-handshake result set after Connection Detector / forceOnlineMode.
            if (!player.isOnlineMode()) {
                return;
            }
            player.getIdentifiedKey();
        } catch (RuntimeException ignored) {
            // Identified keys are optional and version-specific.
        }
    }

    private String detectorVersion() {
        return proxy.getPluginManager().getPlugin("blazemc-connection-detector")
                .map(PluginContainer::getDescription)
                .flatMap(desc -> desc.getVersion())
                .orElse("none");
    }

    private void loadConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve("config.yml");
            if (!Files.exists(file)) {
                Files.writeString(file, defaultConfig(""), StandardCharsets.UTF_8);
            }
            Map<String, Object> yaml = YamlSupport.load(file);
            this.enabled = YamlSupport.bool(yaml, "bridge.enabled", true);
            this.protectReservedNames = YamlSupport.bool(yaml, "premium.protect-reserved-names", true);
            this.rejectUnverified = YamlSupport.bool(yaml, "premium.reject-unverified-sessions", true);
            this.skipLimboForPremium = YamlSupport.bool(yaml, "premium.skip-limbo", true);
            String secretHex = YamlSupport.string(yaml, "bridge.shared-secret", "");
            Path serverRoot = dataDirectory.getParent() != null && dataDirectory.getParent().getParent() != null
                    ? dataDirectory.getParent().getParent()
                    : Path.of(".").toAbsolutePath();
            this.secret = ForwardingSecretLocator.resolve(secretHex, dataDirectory, serverRoot);
            if (this.secret.length == 0) {
                logger.warn("HMAC secret is empty after checking config.yml and forwarding.secret.");
            }
        } catch (IOException ex) {
            logger.error("Unable to load BlazeMCLogin bridge config", ex);
        }
    }

    private static Component unavailableMessage() {
        return Component.text("BlazeMCLogin ∙ Identity verification is temporarily unavailable. Try again later.", NamedTextColor.RED);
    }

    private String defaultConfig(String secret) {
        return """
                # BlazeMCLogin Velocity Bridge
                # Leave shared-secret empty to use Velocity forwarding.secret (zero-config HMAC).
                # Optional override: set the same hex value on Paper premium.yml.
                bridge:
                  enabled: true
                  channel: blazemclogin:v1
                  shared-secret: %s
                premium:
                  protect-reserved-names: true
                  reject-unverified-sessions: true
                  skip-limbo: true
                """.formatted(secret);
    }
}
