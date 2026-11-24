package net.blazemc.login.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import net.blazemc.login.PluginConstants;
import net.blazemc.login.config.YamlSupport;
import net.blazemc.login.premium.PremiumVerification;
import net.blazemc.login.premium.PremiumVerificationCodec;
import net.blazemc.login.premium.PremiumVerifier;
import net.blazemc.login.security.SecureTokens;
import org.slf4j.LoggerFactory;

@Plugin(
        id = "blazemclogin-bridge",
        name = "BlazeMCLogin Velocity Bridge",
        version = "1.0.0",
        authors = {"BlazeMC Network"},
        description = "Sends HMAC-signed Premium verification from Velocity to BlazeMCLogin on Paper"
)
public final class BlazeMCLoginVelocityBridge {

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(PluginConstants.CHANNEL_ID);

    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final org.slf4j.Logger logger;
    private byte[] secret = new byte[0];
    private boolean enabled = true;
    private boolean sendOnlyOnlineMode = true;

    @Inject
    public BlazeMCLoginVelocityBridge(ProxyServer proxy, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        this.logger = LoggerFactory.getLogger("blazemclogin-bridge");
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        loadConfig();
        proxy.getChannelRegistrar().register(CHANNEL);
        boolean detector = proxy.getPluginManager().getPlugin("blazemc-connection-detector").isPresent();
        logger.info("BlazeMCLogin Velocity Bridge enabled. Connection Detector: {}", detector ? "detected" : "not loaded");
        if (secret.length == 0) {
            logger.warn("Shared HMAC secret is empty. Premium auto-login packets will not be sent. Copy the generated secret from config.yml into Paper premium.yml.");
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        if (!enabled || secret.length == 0) {
            return;
        }
        Player player = event.getPlayer();
        boolean premium = player.isOnlineMode();
        if (sendOnlyOnlineMode && !premium) {
            return;
        }
        Optional<ServerConnection> connection = player.getCurrentServer();
        if (connection.isEmpty()) {
            return;
        }
        PremiumVerification verification = PremiumVerificationCodec.create(
                player.getUniqueId(),
                player.getUsername(),
                premium,
                detectorVersion()
        );
        byte[] packet = PremiumVerificationCodec.encode(verification, secret);
        boolean sent = connection.get().sendPluginMessage(CHANNEL, packet);
        if (!sent) {
            logger.debug("Backend did not accept premium verification channel for {}", player.getUsername());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        // No session state is kept in the bridge.
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
            this.sendOnlyOnlineMode = YamlSupport.bool(yaml, "bridge.send-only-when-online-mode", true);
            String secretHex = YamlSupport.string(yaml, "bridge.shared-secret", "");
            if (secretHex == null || secretHex.isBlank() || "CHANGE_ME".equalsIgnoreCase(secretHex)) {
                secretHex = SecureTokens.randomSecret();
                Files.writeString(file, defaultConfig(secretHex), StandardCharsets.UTF_8);
                logger.info("Generated a new HMAC shared secret in config.yml. Copy it to Paper plugins/BlazeMCLogin/premium.yml. The value is not logged.");
            }
            this.secret = PremiumVerifier.parseSecret(secretHex);
        } catch (IOException ex) {
            logger.error("Unable to load BlazeMCLogin bridge config", ex);
        }
    }

    private String defaultConfig(String secret) {
        return """
                # BlazeMCLogin Velocity Bridge
                # Copy shared-secret to Paper: plugins/BlazeMCLogin/premium.yml -> premium.bridge.shared-secret
                # Never commit or log this value.
                #
                # This adapter does not re-implement Mojang authentication. It forwards the
                # already-verified Velocity online-mode result (set by BlazeMC Connection Detector
                # after a real Mojang/Microsoft session) as an HMAC-SHA256 signed packet.
                bridge:
                  enabled: true
                  channel: blazemclogin:v1
                  send-only-when-online-mode: true
                  shared-secret: %s
                """.formatted(secret);
    }

    @SuppressWarnings("unused")
    private Logger jul() {
        return Logger.getLogger("blazemclogin-bridge");
    }

    @SuppressWarnings("unused")
    private InputStream resource(String name) {
        return getClass().getResourceAsStream(name);
    }
}
