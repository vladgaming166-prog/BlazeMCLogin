package net.blazemc.login.paper.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.blazemc.login.auth.PendingPlayer;
import net.blazemc.login.config.MovementSettings;
import net.blazemc.login.config.RestrictionSettings;
import net.blazemc.login.movement.MovementGuard;
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
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

public final class RestrictionListener implements Listener {

    private static final long EMERGENCY_COOLDOWN_MILLIS = 2_000L;

    private final BlazeMCLoginPlugin plugin;

    public RestrictionListener(BlazeMCLoginPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean blocked(Player player) {
        if (!plugin.settings().restrictions().enabled()) {
            return false;
        }
        PendingPlayer pending = plugin.auth().pending(player.getUniqueId());
        return pending != null && !pending.authenticated();
    }

    /**
     * Cancel illegal walking. Do not call {@code setTo} or teleport every tick —
     * those send position packets that desync the client and shove the player forward.
     * Look (yaw/pitch) is preserved because look-only packets are not cancelled.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!blocked(player)) {
            return;
        }
        RestrictionSettings restrictions = plugin.settings().restrictions();
        MovementSettings movement = plugin.settings().movement();
        if (!restrictions.freezePlayer() && !restrictions.disableMove()) {
            return;
        }
        if (movement.enabled() && !movement.freezeUnauthenticated()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        PendingPlayer pending = plugin.auth().pending(player.getUniqueId());
        double freezeY = pending != null ? pending.frozenY() : from.getY();
        boolean cancel = MovementGuard.shouldCancelMove(
                from.getX(), from.getY(), from.getZ(),
                to.getX(), to.getY(), to.getZ(),
                freezeY,
                movement.maxVerticalDrift()
        );
        if (cancel) {
            event.setCancelled(true);
        }
        if (pending != null && movement.preventPositionDesync()
                && MovementGuard.needsEmergencyCorrection(
                pending.frozenX(), pending.frozenY(), pending.frozenZ(),
                to.getX(), to.getY(), to.getZ(),
                movement.emergencyCorrectionBlocks())
                && pending.canEmergencyCorrect(System.currentTimeMillis(), EMERGENCY_COOLDOWN_MILLIS)) {
            Location freeze = freezeLocation(player, pending);
            if (freeze != null) {
                pending.allowNextPluginTeleport();
                player.teleport(freeze);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        if (blocked(event.getPlayer()) && plugin.settings().restrictions().freezePlayer()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().setFlying(false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicle(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && blocked(player)) {
            event.setCancelled(true);
        }
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
        if (!plugin.settings().movement().preventUnexpectedTeleports() && !plugin.settings().restrictions().enabled()) {
            return;
        }
        PendingPlayer pending = plugin.auth().pending(event.getPlayer().getUniqueId());
        if (pending != null && pending.consumePluginTeleportPermit()
                && event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        event.setCancelled(true);
    }

    private static Location freezeLocation(Player player, PendingPlayer pending) {
        if (pending.frozenWorld() == null) {
            Location current = player.getLocation();
            return new Location(current.getWorld(), current.getX(), current.getY(), current.getZ(),
                    current.getYaw(), current.getPitch());
        }
        var world = player.getServer().getWorld(pending.frozenWorld());
        if (world == null) {
            world = player.getWorld();
        }
        return new Location(world, pending.frozenX(), pending.frozenY(), pending.frozenZ(),
                pending.frozenYaw(), pending.frozenPitch());
    }
}
