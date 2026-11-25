package net.blazemc.login.paper.premium;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import net.blazemc.login.paper.BlazeMCLoginPlugin;
import net.blazemc.login.premium.TrustedAuthStatus;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

/**
 * Reads verified PREMIUM/CRACKED status from plugins already trusted on this
 * Paper server. Username caches are never treated as proof of identity.
 */
public final class TrustedAuthProviders {

    public static final String HMAC_BRIDGE = "hmac-bridge";
    public static final String CONNECTION_DETECTOR = "BlazeMC Connection Detector";
    public static final String PREMIUM_OR_CRACKED = "PremiumOrCracked";

    private static final String[] PLUGIN_NAMES = {
            "PremiumOrCracked",
            "premiumorcracked",
            "Premium-Or-Cracked"
    };

    private final BlazeMCLoginPlugin plugin;
    private volatile String detectedPremiumOrCracked = "not loaded";

    public TrustedAuthProviders(BlazeMCLoginPlugin plugin) {
        this.plugin = plugin;
        refreshDetected();
    }

    public void refreshDetected() {
        Plugin found = findPremiumOrCrackedPlugin();
        this.detectedPremiumOrCracked = found == null
                ? "not loaded"
                : found.getName() + " " + pluginVersion(found);
    }

    public String detectedPremiumOrCracked() {
        return detectedPremiumOrCracked;
    }

    public boolean premiumOrCrackedPresent() {
        return findPremiumOrCrackedPlugin() != null;
    }

    public Probe probe(Player player) {
        Probe fromPlugin = probePremiumOrCracked(player);
        if (fromPlugin.status() != TrustedAuthStatus.PENDING) {
            return fromPlugin;
        }
        Probe fromMeta = probeMetadata(player);
        if (fromMeta.status() != TrustedAuthStatus.PENDING) {
            return fromMeta;
        }
        return probePlaceholderApi(player);
    }

    public Probe probePremiumOrCracked(Player player) {
        Plugin found = findPremiumOrCrackedPlugin();
        if (found == null) {
            return Probe.pending("PremiumOrCracked");
        }
        Object instance = found;
        Probe methodProbe = invokeStatusMethods(instance, player, PREMIUM_OR_CRACKED);
        if (methodProbe.status() != TrustedAuthStatus.PENDING) {
            return methodProbe;
        }
        try {
            Method getApi = instance.getClass().getMethod("getAPI");
            Object api = getApi.invoke(instance);
            if (api != null) {
                Probe apiProbe = invokeStatusMethods(api, player, PREMIUM_OR_CRACKED);
                if (apiProbe.status() != TrustedAuthStatus.PENDING) {
                    return apiProbe;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Optional API accessor.
        }
        try {
            Method getInstance = instance.getClass().getMethod("getInstance");
            Object api = getInstance.invoke(null);
            if (api != null && api != instance) {
                Probe apiProbe = invokeStatusMethods(api, player, PREMIUM_OR_CRACKED);
                if (apiProbe.status() != TrustedAuthStatus.PENDING) {
                    return apiProbe;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Optional singleton.
        }
        return Probe.pending(PREMIUM_OR_CRACKED);
    }

    private Probe invokeStatusMethods(Object target, Player player, String provider) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String[] names = {
                "isPremium", "isUserPremium", "isPlayerPremium", "hasPremium",
                "isCracked", "isUserCracked", "getStatus", "getUserStatus",
                "getAccountType", "getType", "getPlayerType", "checkStatus"
        };
        for (String methodName : names) {
            Probe probe = invokeNamed(target, methodName, player, uuid, name, provider);
            if (probe.status() != TrustedAuthStatus.PENDING) {
                return probe;
            }
        }
        return Probe.pending(provider);
    }

    private Probe invokeNamed(
            Object target,
            String methodName,
            Player player,
            UUID uuid,
            String name,
            String provider
    ) {
        Object[][] argumentSets = {
                {player},
                {uuid},
                {name},
                {player, uuid},
                {uuid, name}
        };
        Class<?> type = target.getClass();
        for (Object[] args : argumentSets) {
            Method method = findMethod(type, methodName, args);
            if (method == null) {
                continue;
            }
            try {
                Object result = method.invoke(target, args);
                TrustedAuthStatus status = interpret(result, methodName);
                if (status != TrustedAuthStatus.PENDING) {
                    return new Probe(status, provider);
                }
            } catch (ReflectiveOperationException ex) {
                plugin.getLogger().log(Level.FINE, "PremiumOrCracked method " + methodName + " failed", ex);
            }
        }
        return Probe.pending(provider);
    }

    private static Method findMethod(Class<?> type, String name, Object[] args) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            boolean match = true;
            for (int i = 0; i < types.length; i++) {
                if (args[i] == null || !types[i].isAssignableFrom(args[i].getClass())) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return method;
            }
        }
        return null;
    }

    private Probe probeMetadata(Player player) {
        String[] keys = {
                "PremiumOrCracked", "premiumorcracked", "premium_or_cracked",
                "premium", "accountType", "account-type", "FastLogin", "fastlogin_status"
        };
        for (String key : keys) {
            List<MetadataValue> values = player.getMetadata(key);
            for (MetadataValue value : values) {
                TrustedAuthStatus status = interpret(value.value(), key);
                if (status != TrustedAuthStatus.PENDING) {
                    String provider = value.getOwningPlugin() == null ? key : value.getOwningPlugin().getName();
                    return new Probe(status, provider);
                }
            }
        }
        return Probe.pending("metadata");
    }

    private Probe probePlaceholderApi(Player player) {
        Plugin papi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            return Probe.pending("PlaceholderAPI");
        }
        String[] placeholders = {
                "%premiumorcracked_status%",
                "%premium_or_cracked_status%",
                "%fastlogin_status%"
        };
        try {
            Class<?> expansion = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method set = expansion.getMethod("setPlaceholders", Player.class, String.class);
            for (String placeholder : placeholders) {
                Object rendered = set.invoke(null, player, placeholder);
                TrustedAuthStatus status = interpret(rendered, placeholder);
                if (status != TrustedAuthStatus.PENDING) {
                    return new Probe(status, "PlaceholderAPI:" + placeholder);
                }
            }
        } catch (ClassNotFoundException ignored) {
            return Probe.pending("PlaceholderAPI");
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.FINE, "PlaceholderAPI premium probe failed", ex);
        }
        return Probe.pending("PlaceholderAPI");
    }

    static TrustedAuthStatus interpret(Object value, String hint) {
        if (value == null) {
            return TrustedAuthStatus.PENDING;
        }
        if (value instanceof Boolean bool) {
            if (hint.toLowerCase(Locale.ROOT).contains("cracked")) {
                return bool ? TrustedAuthStatus.CRACKED : TrustedAuthStatus.PENDING;
            }
            return bool ? TrustedAuthStatus.PREMIUM : TrustedAuthStatus.PENDING;
        }
        if (value instanceof Enum<?> enumerated) {
            return interpret(enumerated.name(), hint);
        }
        String text = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        if (text.isEmpty() || text.equals("UNKNOWN") || text.equals("PENDING")
                || text.equals("NONE") || text.equals("%PREMIUMORCRACKED_STATUS%")
                || text.equals("%FASTLOGIN_STATUS%") || text.equals("%PREMIUM_OR_CRACKED_STATUS%")) {
            return TrustedAuthStatus.PENDING;
        }
        if (text.contains("PREMIUM") || text.equals("ONLINE") || text.equals("PAID") || text.equals("TRUE")) {
            return TrustedAuthStatus.PREMIUM;
        }
        if (text.contains("CRACKED") || text.equals("OFFLINE") || text.equals("FALSE") || text.contains("NOT_PREMIUM")) {
            return TrustedAuthStatus.CRACKED;
        }
        return TrustedAuthStatus.PENDING;
    }

    private static String pluginVersion(Plugin found) {
        try {
            Method meta = found.getClass().getMethod("getPluginMeta");
            Object pluginMeta = meta.invoke(found);
            Object version = pluginMeta.getClass().getMethod("getVersion").invoke(pluginMeta);
            return String.valueOf(version);
        } catch (ReflectiveOperationException ignored) {
            return found.getDescription().getVersion();
        }
    }

    private Plugin findPremiumOrCrackedPlugin() {
        for (String name : PLUGIN_NAMES) {
            Plugin found = plugin.getServer().getPluginManager().getPlugin(name);
            if (found != null && found.isEnabled()) {
                return found;
            }
        }
        for (Plugin loaded : plugin.getServer().getPluginManager().getPlugins()) {
            String name = loaded.getName().replace(" ", "").replace("-", "").toLowerCase(Locale.ROOT);
            if (name.equals("premiumorcracked") && loaded.isEnabled()) {
                return loaded;
            }
        }
        return null;
    }

    public record Probe(TrustedAuthStatus status, String provider) {
        public static Probe pending(String provider) {
            return new Probe(TrustedAuthStatus.PENDING, provider);
        }
    }
}
