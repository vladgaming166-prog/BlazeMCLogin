package net.blazemc.login.premium;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import net.blazemc.login.config.PremiumSettings;

public final class PremiumVerifier {

    private final NonceCache nonces;

    public PremiumVerifier(long nonceTtlMillis) {
        this.nonces = new NonceCache(nonceTtlMillis);
    }

    public VerificationResult verify(
            byte[] packet,
            byte[] secret,
            PremiumSettings settings,
            UUID expectedUuid,
            String expectedUsername,
            long nowMillis
    ) {
        if (secret == null || secret.length == 0) {
            return VerificationResult.fail(VerificationOutcome.MISSING_SECRET);
        }
        PremiumVerificationCodec.DecodeResult decoded = PremiumVerificationCodec.decode(packet, secret);
        if (!decoded.accepted()) {
            return VerificationResult.fail(decoded.outcome());
        }
        PremiumVerification verification = decoded.verification();
        long maxAge = Math.max(1, settings.maxMessageAgeSeconds()) * 1000L;
        if (Math.abs(nowMillis - verification.timestampMillis()) > maxAge) {
            return VerificationResult.fail(VerificationOutcome.EXPIRED);
        }
        if (nonces.seen(verification.nonce(), nowMillis)) {
            return VerificationResult.fail(VerificationOutcome.REPLAY);
        }
        if (expectedUuid != null && !expectedUuid.equals(verification.uuid())) {
            return VerificationResult.fail(VerificationOutcome.UUID_MISMATCH);
        }
        if (expectedUsername != null
                && !expectedUsername.toLowerCase(Locale.ROOT).equals(verification.username().toLowerCase(Locale.ROOT))) {
            return VerificationResult.fail(VerificationOutcome.USERNAME_MISMATCH);
        }
        if (!verification.premium()) {
            return new VerificationResult(VerificationOutcome.CRACKED_SESSION, verification);
        }
        return VerificationResult.ok(verification);
    }

    /**
     * Signature, expiry, replay and identity checks without requiring {@code premium=true}.
     */
    public VerificationResult inspect(
            byte[] packet,
            byte[] secret,
            PremiumSettings settings,
            UUID expectedUuid,
            String expectedUsername,
            long nowMillis
    ) {
        return verify(packet, secret, settings, expectedUuid, expectedUsername, nowMillis);
    }

    public static byte[] parseSecret(String hex) {
        if (hex == null) {
            return new byte[0];
        }
        String value = hex.trim();
        if (value.isEmpty() || "CHANGE_ME".equalsIgnoreCase(value) || "changeme".equalsIgnoreCase(value)) {
            return new byte[0];
        }
        try {
            if ((value.length() & 1) == 1) {
                return new byte[0];
            }
            return HexFormat.of().parseHex(value);
        } catch (RuntimeException ex) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }

    public record VerificationResult(VerificationOutcome outcome, PremiumVerification verification) {
        public static VerificationResult ok(PremiumVerification verification) {
            return new VerificationResult(VerificationOutcome.ACCEPTED, verification);
        }

        public static VerificationResult fail(VerificationOutcome outcome) {
            return new VerificationResult(outcome, null);
        }

        public boolean accepted() {
            return outcome == VerificationOutcome.ACCEPTED && verification != null;
        }
    }
}
