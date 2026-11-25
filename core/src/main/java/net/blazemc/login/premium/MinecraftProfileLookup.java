package net.blazemc.login.premium;

public interface MinecraftProfileLookup {

    enum ProfileStatus {
        PREMIUM,
        CRACKED,
        UNAVAILABLE
    }

    ProfileStatus lookup(String username);
}
