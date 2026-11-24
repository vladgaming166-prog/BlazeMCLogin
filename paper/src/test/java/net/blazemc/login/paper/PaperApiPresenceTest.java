package net.blazemc.login.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PaperApiPresenceTest {

    @Test
    void requiredPaperApisExist() {
        assertNotNull(JavaPlugin.class);
        assertNotNull(Player.class);
        assertNotNull(PlayerJoinEvent.class);
        assertNotNull(AsyncChatEvent.class);
        assertNotNull(org.bukkit.command.CommandExecutor.class);
        assertNotNull(org.bukkit.plugin.messaging.PluginMessageListener.class);
    }
}
