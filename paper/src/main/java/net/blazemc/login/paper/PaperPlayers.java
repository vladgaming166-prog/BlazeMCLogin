package net.blazemc.login.paper;

import java.net.InetSocketAddress;
import java.util.Locale;
import net.blazemc.login.ip.IpAddressNormalizer;
import org.bukkit.entity.Player;

public final class PaperPlayers {

    private PaperPlayers() {
    }

    public static String normalizedIp(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) {
            return "0.0.0.0";
        }
        return IpAddressNormalizer.normalize(address.getAddress().getHostAddress());
    }

    public static boolean hasBypassLogin(Player player) {
        return player.hasPermission("blazemclogin.bypass.login");
    }

    public static boolean hasBypassIp(Player player) {
        return player.hasPermission("blazemclogin.bypass.iplimit");
    }

    public static String commandRoot(String message) {
        String value = message.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        int space = value.indexOf(' ');
        if (space >= 0) {
            value = value.substring(0, space);
        }
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        return value;
    }
}
