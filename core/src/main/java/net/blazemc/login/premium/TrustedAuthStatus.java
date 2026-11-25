package net.blazemc.login.premium;

/**
 * Session class from a trusted provider. Username lists and caches are not a source.
 */
public enum TrustedAuthStatus {
    PENDING,
    PREMIUM,
    CRACKED
}
