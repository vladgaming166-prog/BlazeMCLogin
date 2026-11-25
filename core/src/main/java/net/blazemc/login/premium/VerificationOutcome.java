package net.blazemc.login.premium;

public enum VerificationOutcome {
    ACCEPTED,
    MISSING_SECRET,
    MALFORMED,
    BAD_SIGNATURE,
    EXPIRED,
    REPLAY,
    UUID_MISMATCH,
    USERNAME_MISMATCH,
    NOT_PREMIUM,
    CRACKED_SESSION,
    TOO_EARLY
}
