package net.blazemc.login.velocity;

import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Method;
import org.slf4j.Logger;

/**
 * Optional LimboAPI integration. Verified Premium players skip the login limbo
 * so the transfer onto the backend does not apply an extra position correction.
 * LimboAPI is not required; this is a no-op when the event class is absent.
 */
public final class LimboSkipHook {

    private LimboSkipHook() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(ProxyServer proxy, Object plugin, Logger logger) {
        try {
            Class<?> eventClass = Class.forName("net.elytrium.limboapi.api.event.LoginLimboRegisterEvent");
            EventHandler handler = event -> skipPremiumLimbo(event, logger);
            proxy.getEventManager().register(plugin, eventClass, handler);
            logger.info("LimboAPI detected; verified Premium players will skip the login limbo.");
        } catch (ClassNotFoundException ex) {
            logger.info("LimboAPI not present; premium players will use the normal Velocity connect path.");
        }
    }

    static void skipPremiumLimbo(Object event, Logger logger) {
        try {
            Method getPlayer = event.getClass().getMethod("getPlayer");
            Object playerObj = getPlayer.invoke(event);
            if (!(playerObj instanceof Player player) || !player.isOnlineMode()) {
                return;
            }
            event.getClass().getMethod("clearCallback").invoke(event);
        } catch (ReflectiveOperationException ex) {
            logger.debug("LimboAPI skip hook could not run: {}", ex.toString());
        }
    }
}
