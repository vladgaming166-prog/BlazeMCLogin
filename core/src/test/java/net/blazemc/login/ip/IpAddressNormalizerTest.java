package net.blazemc.login.ip;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IpAddressNormalizerTest {

    @Test
    void normalizesIpv4AndStripsPort() {
        assertThat(IpAddressNormalizer.normalize("127.0.0.1")).isEqualTo("127.0.0.1");
        assertThat(IpAddressNormalizer.normalize("192.168.1.20:25565")).isEqualTo("192.168.1.20");
        assertThat(IpAddressNormalizer.normalize("/10.0.0.1")).isEqualTo("10.0.0.1");
    }

    @Test
    void normalizesIpv6AndMappedAddresses() {
        assertThat(IpAddressNormalizer.normalize("2001:db8::1")).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(IpAddressNormalizer.normalize("[2001:db8::1]:25565")).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(IpAddressNormalizer.normalize("::ffff:192.0.2.1")).isEqualTo("192.0.2.1");
    }

    @Test
    void rejectsHostnamesToAvoidDnsTrust() {
        assertThat(IpAddressNormalizer.isLiteralIp("example.com")).isFalse();
        assertThatThrownBy(() -> IpAddressNormalizer.normalize("example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashedIpsAreStable() {
        IpHasher hasher = new IpHasher(new byte[32]);
        String a = hasher.hashRaw("192.168.0.5:25565");
        String b = hasher.hashNormalized("192.168.0.5");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSize(64);
        assertThat(hasher.hashRaw("2001:db8::1")).isNotEqualTo(a);
    }
}
