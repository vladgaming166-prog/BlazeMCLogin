package net.blazemc.login.premium;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PremiumPacketBufferTest {

    @Test
    void storesThenTakesOnceBeforeExpiry() {
        PremiumPacketBuffer buffer = new PremiumPacketBuffer(5_000);
        UUID uuid = UUID.randomUUID();
        PremiumVerification verification = PremiumVerificationCodec.create(uuid, "Steve", true, "1");
        buffer.store(verification);
        assertThat(buffer.take(uuid, verification.timestampMillis())).isEqualTo(verification);
        assertThat(buffer.take(uuid, verification.timestampMillis())).isNull();
    }

    @Test
    void expiredPacketsAreDiscarded() {
        PremiumPacketBuffer buffer = new PremiumPacketBuffer(10);
        UUID uuid = UUID.randomUUID();
        PremiumVerification verification = new PremiumVerification(uuid, "Steve", true, 1L, new byte[16], "1");
        buffer.store(verification);
        assertThat(buffer.take(uuid, 10_000L)).isNull();
    }
}
