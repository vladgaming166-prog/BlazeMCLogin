package net.blazemc.login.paper.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.blazemc.login.auth.PendingPlayer;
import net.blazemc.login.config.RestrictionSettings;
import net.blazemc.login.paper.BlazeMCLoginPlugin;
import net.blazemc.login.paper.PaperPlayers;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class RestrictionListener implements Listener {

    private final BlazeMCLoginPlugin plugin;

    public RestrictionListener(BlazeMCLoginPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean blocked(Player player) {
        if (!plugin.settings().restrictions().enabled()) {
            return false;
        }
        PendingPlayer pending = plugin.auth().pending(player.getUniqueId());
        return pending == null || !pending.authenticated();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!blocked(event.getPlayer()) || !plugin.settings().restrictions().freezePlayer()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        PendingPlayer pending = plugin.auth().pending(event.getPlayer().getUniqueId());
        if (pending != null && pending.frozenWorld() != null) {
            var world = plugin.getServer().getWorld(pending.frozenWorld());
            if (world != null) {
                event.setTo(new Location(world, pending.frozenX(), pending.frozenY(), pending.frozenZ(), pending.frozenYaw(), pending.frozenPitch()));
                return;
            }
        }
        event.setTo(from);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (blocked(event.getPlayer()) && plugin.settings().restrictions().disableChat()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!blocked(event.getPlayer()) || !plugin.settings().restrictions().disableCommands()) {
            return;
        }
        String root = RestrictionSettings.normalizeCommand(PaperPlayers.commandRoot(event.getMessage()));
        if (!plugin.settings().restrictions().allowedCommandRoots().contains(root)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (blocked(event.getPlayer()) && plugin.settings().restrictions().disableDrop()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && blocked(player) && plugin.settings().restrictions().disablePickup()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && blocked(player) && plugin.settings().restrictions().disableInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && blocked(player) && plugin.settings().restrictions().disableInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && blocked(player) && plugin.settings().restrictions().disableInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (blocked(event.getPlayer()) && plugin.settings().restrictions().disableInteractions()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (blocked(event.getPlayer()) && plugin.settings().restrictions().disableInteractions()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (blocked(event.getPlayer()) && plugin.settings().restrictions().disableInteractions()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (blocked(event.getPlayer()) && plugin.settings().restrictions().disableInteractions()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && blocked(player) && plugin.settings().restrictions().disableDamage()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && blocked(player) && plugin.settings().restrictions().disableDamage()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && blocked(player) && plugin.settings().restrictions().disableDamage()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (blocked(event.getPlayer()) && plugin.settings().restrictions().disableInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!blocked(event.getPlayer())) {
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                || event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            return;
        }
        event.setCancelled(true);
    }
}
