package net.blazemc.login.config;

import java.util.Map;

public record SoundSettings(
        boolean enabled,
        SoundEventSettings loginPrompt,
        SoundEventSettings registerPrompt,
        SoundEventSettings loginSuccess,
        SoundEventSettings registerSuccess,
        SoundEventSettings premiumAuto,
        SoundEventSettings wrongPassword,
        SoundEventSettings authError,
        SoundEventSettings premiumDenied,
        SoundEventSettings connected
) {
    public static SoundSettings defaults() {
        return new SoundSettings(
                true,
                SoundEventSettings.of("minecraft:block.note_block.pling", 0.8f, 1.2f),
                SoundEventSettings.of("minecraft:block.note_block.harp", 0.7f, 0.8f),
                SoundEventSettings.of("minecraft:entity.player.levelup", 0.7f, 1.2f),
                SoundEventSettings.of("minecraft:entity.player.levelup", 0.9f, 1.0f),
                SoundEventSettings.of("minecraft:entity.player.levelup", 0.85f, 1.5f),
                SoundEventSettings.of("minecraft:entity.villager.no", 0.9f, 0.9f),
                SoundEventSettings.of("minecraft:block.note_block.bass", 0.8f, 0.6f),
                SoundEventSettings.of("minecraft:entity.villager.no", 1.0f, 0.7f),
                SoundEventSettings.of("minecraft:entity.experience_orb.pickup", 0.7f, 1.1f)
        );
    }

    public static SoundSettings from(Map<String, Object> root, Map<String, Object> overlay) {
        SoundSettings defaults = defaults();
        boolean enabled = YamlSupport.boolOverlay(defaults.enabled(), "sounds.enabled", overlay, root);
        return new SoundSettings(
                enabled,
                event(root, overlay, "sounds.login-prompt", defaults.loginPrompt()),
                event(root, overlay, "sounds.register-prompt", defaults.registerPrompt()),
                event(root, overlay, "sounds.login-success", defaults.loginSuccess()),
                event(root, overlay, "sounds.register-success", defaults.registerSuccess()),
                event(root, overlay, "sounds.premium-auto", defaults.premiumAuto()),
                event(root, overlay, "sounds.wrong-password", defaults.wrongPassword()),
                event(root, overlay, "sounds.auth-error", defaults.authError()),
                event(root, overlay, "sounds.premium-denied", defaults.premiumDenied()),
                event(root, overlay, "sounds.connected", defaults.connected())
        );
    }

    private static SoundEventSettings event(
            Map<String, Object> root,
            Map<String, Object> overlay,
            String path,
            SoundEventSettings fallback
    ) {
        boolean enabled = YamlSupport.boolOverlay(fallback.enabled(), path + ".enabled", overlay, root);
        String sound = YamlSupport.stringOverlay(fallback.sound(), path + ".sound", overlay, root);
        float volume = (float) YamlSupport.decimalOverlay(fallback.volume(), path + ".volume", overlay, root);
        float pitch = (float) YamlSupport.decimalOverlay(fallback.pitch(), path + ".pitch", overlay, root);
        return new SoundEventSettings(enabled, sound, volume, pitch);
    }
}
