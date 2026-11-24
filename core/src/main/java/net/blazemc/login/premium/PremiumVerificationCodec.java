package net.blazemc.login.premium;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import net.blazemc.login.PluginConstants;
import net.blazemc.login.security.HmacSha256;
import net.blazemc.login.security.SecureTokens;

/**
 * Binary HMAC-SHA256 plugin-message protocol shared by the Velocity bridge and Paper.
 *
 * <pre>
 * magic u32 'BML1'
 * version u8
 * uuid msb i64, lsb i64
 * username utf
 * premium u8
 * timestamp i64
 * nonce 16 bytes
 * detector utf
 * hmac 32 bytes covering every preceding field
 * </pre>
 */
public final class PremiumVerificationCodec {

    private PremiumVerificationCodec() {
    }

    public static byte[] encode(PremiumVerification verification, byte[] secret) {
        try {
            byte[] body = encodeBody(verification);
            byte[] mac = HmacSha256.sign(secret, body);
            ByteArrayOutputStream out = new ByteArrayOutputStream(body.length + 32);
            out.write(body);
            out.write(mac);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static PremiumVerification create(UUID uuid, String username, boolean premium, String detectorVersion) {
        return new PremiumVerification(
                uuid,
                username,
                premium,
                System.currentTimeMillis(),
                SecureTokens.randomBytes(16),
                detectorVersion
        );
    }

    public static DecodeResult decode(byte[] packet, byte[] secret) {
        if (packet == null || packet.length < 16 + 32) {
            return DecodeResult.malformed();
        }
        byte[] body = Arrays.copyOfRange(packet, 0, packet.length - 32);
        byte[] mac = Arrays.copyOfRange(packet, packet.length - 32, packet.length);
        if (secret == null || secret.length == 0) {
            return DecodeResult.fail(VerificationOutcome.MISSING_SECRET);
        }
        if (!HmacSha256.verify(secret, body, mac)) {
            return DecodeResult.fail(VerificationOutcome.BAD_SIGNATURE);
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            int magic = in.readInt();
            if (magic != PluginConstants.MAGIC) {
                return DecodeResult.malformed();
            }
            int version = in.readUnsignedByte();
            if (version != PluginConstants.PROTOCOL_VERSION) {
                return DecodeResult.malformed();
            }
            long msb = in.readLong();
            long lsb = in.readLong();
            String username = in.readUTF();
            boolean premium = in.readBoolean();
            long timestamp = in.readLong();
            byte[] nonce = in.readNBytes(16);
            if (nonce.length != 16) {
                return DecodeResult.malformed();
            }
            String detector = in.readUTF();
            if (in.available() != 0) {
                return DecodeResult.malformed();
            }
            return DecodeResult.ok(new PremiumVerification(
                    new UUID(msb, lsb), username, premium, timestamp, nonce, detector));
        } catch (IOException ex) {
            return DecodeResult.malformed();
        }
    }

    private static byte[] encodeBody(PremiumVerification verification) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(PluginConstants.MAGIC);
        out.writeByte(PluginConstants.PROTOCOL_VERSION);
        out.writeLong(verification.uuid().getMostSignificantBits());
        out.writeLong(verification.uuid().getLeastSignificantBits());
        out.writeUTF(verification.username());
        out.writeBoolean(verification.premium());
        out.writeLong(verification.timestampMillis());
        out.write(verification.nonce());
        out.writeUTF(verification.detectorVersion());
        out.flush();
        return bytes.toByteArray();
    }

    public static final class DecodeResult {
        private final VerificationOutcome outcome;
        private final PremiumVerification verification;

        private DecodeResult(VerificationOutcome outcome, PremiumVerification verification) {
            this.outcome = outcome;
            this.verification = verification;
        }

        public static DecodeResult ok(PremiumVerification verification) {
            return new DecodeResult(VerificationOutcome.ACCEPTED, verification);
        }

        public static DecodeResult fail(VerificationOutcome outcome) {
            return new DecodeResult(outcome, null);
        }

        public static DecodeResult malformed() {
            return fail(VerificationOutcome.MALFORMED);
        }

        public VerificationOutcome outcome() {
            return outcome;
        }

        public PremiumVerification verification() {
            return verification;
        }

        public boolean accepted() {
            return outcome == VerificationOutcome.ACCEPTED && verification != null;
        }
    }

    public static byte[] utf(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
