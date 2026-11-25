package net.blazemc.login.config;

public record SoundEventSettings(
        boolean enabled,
        String sound,
        float volume,
        float pitch
) {
    public static SoundEventSettings of(String sound, float volume, float pitch) {
        return new SoundEventSettings(true, sound, volume, pitch);
    }

    public static SoundEventSettings disabled(String sound) {
        return new SoundEventSettings(false, sound, 1.0f, 1.0f);
    }
}
