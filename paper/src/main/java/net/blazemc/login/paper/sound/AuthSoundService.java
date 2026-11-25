package net.blazemc.login.paper.sound;

import net.blazemc.login.config.SoundEventSettings;
import net.blazemc.login.config.SoundSettings;
import net.blazemc.login.paper.BlazeMCLoginPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;

public final class AuthSoundService {

    private final BlazeMCLoginPlugin plugin;

    public AuthSoundService(BlazeMCLoginPlugin plugin) {
        this.plugin = plugin;
    }

    public void loginPrompt(Player player) {
        play(player, settings().loginPrompt());
    }

    public void registerPrompt(Player player) {
        play(player, settings().registerPrompt());
    }

    public void loginSuccess(Player player) {
        play(player, settings().loginSuccess());
    }

    public void registerSuccess(Player player) {
        play(player, settings().registerSuccess());
    }

    public void premiumAuto(Player player) {
        play(player, settings().premiumAuto());
    }

    public void wrongPassword(Player player) {
        play(player, settings().wrongPassword());
    }

    public void authError(Player player) {
        play(player, settings().authError());
    }

    public void premiumDenied(Player player) {
        play(player, settings().premiumDenied());
    }

    public void connected(Player player) {
        play(player, settings().connected());
    }

    public void playOutcome(Player player, net.blazemc.login.auth.AuthOutcome outcome) {
        switch (outcome) {
            case REGISTERED -> registerSuccess(player);
            case LOGGED_IN, SESSION_RESTORED, BYPASS -> {
                loginSuccess(player);
                connected(player);
            }
            case PREMIUM_AUTO_LOGIN -> {
                premiumAuto(player);
                connected(player);
            }
            case WRONG_PASSWORD -> wrongPassword(player);
            case PREMIUM_NAME_PROTECTED, PREMIUM_PASSWORD_DISABLED, PREMIUM_REGISTER_DISABLED -> premiumDenied(player);
            case PASSWORD_EMPTY, PASSWORD_TOO_SHORT, PASSWORD_TOO_LONG, PASSWORD_TOO_COMMON,
                    PASSWORD_COMPLEXITY, PASSWORD_MISMATCH, ACCOUNT_LOCKED, RATE_LIMITED,
                    IP_LIMIT, IP_BLACKLISTED, DATABASE_ERROR, VERIFICATION_UNAVAILABLE,
                    CRACKED_DISABLED, AUTH_IN_PROGRESS, AWAITING_VERIFICATION -> authError(player);
            default -> {
            }
        }
    }

    private SoundSettings settings() {
        return plugin.settings().sounds();
    }

    private void play(Player player, SoundEventSettings event) {
        if (player == null || !player.isOnline()) {
            return;
        }
        SoundSettings sounds = settings();
        if (!sounds.enabled() || event == null || !event.enabled()) {
            return;
        }
        String raw = event.sound() == null ? "" : event.sound().trim();
        if (raw.isEmpty()) {
            return;
        }
        if (raw.startsWith("minecraft:")) {
            raw = raw.substring("minecraft:".length());
        }
        try {
            Key key = Key.key("minecraft", raw.replace("minecraft:", ""));
            Sound sound = Sound.sound(key, Sound.Source.MASTER, event.volume(), event.pitch());
            player.playSound(sound);
        } catch (RuntimeException ex) {
            plugin.getLogger().fine("Skipping unknown sound " + event.sound());
        }
    }
}
