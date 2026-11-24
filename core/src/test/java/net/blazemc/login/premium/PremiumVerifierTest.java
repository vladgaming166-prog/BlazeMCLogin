package net.blazemc.login.premium;

import java.util.UUID;
import net.blazemc.login.config.PremiumSettings;
import net.blazemc.login.security.SecureTokens;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PremiumVerifierTest {

    private final byte[] secret = SecureTokens.randomBytes(32);
    private final PremiumVerifier verifier = new PremiumVerifier(60_000);
    private final PremiumSettings settings = PremiumSettings.defaults();
    private final UUID uuid = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void acceptsValidSignedPacket() {
        PremiumVerification verification = PremiumVerificationCodec.create(uuid, "Steve", true, "1.1.0");
        byte[] packet = PremiumVerificationCodec.encode(verification, secret);
        PremiumVerifier.VerificationResult result = verifier.verify(packet, secret, settings, uuid, "Steve", verification.timestampMillis());
        assertThat(result.accepted()).isTrue();
        assertThat(result.verification().username()).isEqualTo("Steve");
    }

    @Test
    void rejectsInvalidHmac() {
        PremiumVerification verification = PremiumVerificationCodec.create(uuid, "Steve", true, "1.1.0");
        byte[] packet = PremiumVerificationCodec.encode(verification, secret);
        packet[packet.length - 1] ^= 0x01;
        PremiumVerifier.VerificationResult result = verifier.verify(packet, secret, settings, uuid, "Steve", verification.timestampMillis());
        assertThat(result.outcome()).isEqualTo(VerificationOutcome.BAD_SIGNATURE);
    }

    @Test
    void rejectsReplay() {
        PremiumVerification verification = PremiumVerificationCodec.create(uuid, "Steve", true, "1.1.0");
        byte[] packet = PremiumVerificationCodec.encode(verification, secret);
        assertThat(verifier.verify(packet, secret, settings, uuid, "Steve", verification.timestampMillis()).accepted()).isTrue();
        assertThat(verifier.verify(packet, secret, settings, uuid, "Steve", verification.timestampMillis()).outcome())
                .isEqualTo(VerificationOutcome.REPLAY);
    }

    @Test
    void rejectsExpiredPacket() {
        PremiumVerification old = new PremiumVerification(uuid, "Steve", true, 1L, SecureTokens.randomBytes(16), "1.1.0");
        byte[] packet = PremiumVerificationCodec.encode(old, secret);
        assertThat(verifier.verify(packet, secret, settings, uuid, "Steve", System.currentTimeMillis()).outcome())
                .isEqualTo(VerificationOutcome.EXPIRED);
    }

    @Test
    void rejectsUuidMismatchAndMissingSecret() {
        PremiumVerification verification = PremiumVerificationCodec.create(uuid, "Steve", true, "1.1.0");
        byte[] packet = PremiumVerificationCodec.encode(verification, secret);
        assertThat(verifier.verify(packet, secret, settings, UUID.randomUUID(), "Steve", verification.timestampMillis()).outcome())
                .isEqualTo(VerificationOutcome.UUID_MISMATCH);
        assertThat(verifier.verify(packet, new byte[0], settings, uuid, "Steve", verification.timestampMillis()).outcome())
                .isEqualTo(VerificationOutcome.MISSING_SECRET);
    }

    @Test
    void neverTrustsUnsignedPremiumFlagAlone() {
        PremiumVerification verification = PremiumVerificationCodec.create(uuid, "Steve", true, "1.1.0");
        byte[] packet = PremiumVerificationCodec.encode(verification, secret);
        byte[] otherSecret = SecureTokens.randomBytes(32);
        assertThat(verifier.verify(packet, otherSecret, settings, uuid, "Steve", verification.timestampMillis()).outcome())
                .isEqualTo(VerificationOutcome.BAD_SIGNATURE);
    }
}
