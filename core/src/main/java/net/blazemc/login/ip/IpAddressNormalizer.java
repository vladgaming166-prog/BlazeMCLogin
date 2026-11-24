package net.blazemc.login.ip;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Normalizes literal IPv4 and IPv6 addresses without performing DNS lookups.
 */
public final class IpAddressNormalizer {

    private static final Pattern IPV4 = Pattern.compile(
            "^(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.%]+$");

    private IpAddressNormalizer() {
    }

    public static String normalize(String raw) {
        Objects.requireNonNull(raw, "address");
        String host = stripHost(raw);
        if (host.isEmpty()) {
            throw new IllegalArgumentException("empty address");
        }
        try {
            if (isIpv4(host)) {
                return InetAddress.getByName(host).getHostAddress();
            }
            if (isIpv6Literal(host)) {
                InetAddress address = InetAddress.getByName(host);
                if (address instanceof Inet6Address inet6) {
                    byte[] bytes = inet6.getAddress();
                    if (isIpv4Mapped(bytes)) {
                        return (bytes[12] & 0xff) + "." + (bytes[13] & 0xff) + "."
                                + (bytes[14] & 0xff) + "." + (bytes[15] & 0xff);
                    }
                }
                return stripZone(address.getHostAddress()).toLowerCase(Locale.ROOT);
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("invalid IP address: " + host, ex);
        }
        throw new IllegalArgumentException("not a literal IP address: " + host);
    }

    public static boolean isLiteralIp(String raw) {
        try {
            normalize(raw);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    static String stripHost(String raw) {
        String value = raw.trim();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        int slash = value.indexOf('/');
        if (slash > 0 && value.regionMatches(true, slash, "/32", 0, 3) == false
                && (value.contains(".") || value.contains(":"))) {
            // keep CIDR out of host; callers that need CIDR parse separately
        }
        if (value.startsWith("[") && value.contains("]")) {
            int end = value.indexOf(']');
            String inner = value.substring(1, end);
            int zone = inner.indexOf('%');
            if (zone >= 0) {
                inner = inner.substring(0, zone);
            }
            return inner;
        }
        int zone = value.indexOf('%');
        if (zone >= 0) {
            value = value.substring(0, zone);
        }
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon && value.indexOf('.') >= 0) {
            String maybeIpv4 = value.substring(0, colon);
            if (IPV4.matcher(maybeIpv4).matches()) {
                value = maybeIpv4;
            }
        }
        return value;
    }

    private static boolean isIpv4(String host) {
        return IPV4.matcher(host).matches();
    }

    private static boolean isIpv6Literal(String host) {
        if (!host.contains(":")) {
            return false;
        }
        if (!IPV6_CHARS.matcher(host).matches()) {
            return false;
        }
        if (host.chars().filter(ch -> ch == ':').count() < 2) {
            return false;
        }
        return true;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static String stripZone(String hostAddress) {
        int zone = hostAddress.indexOf('%');
        if (zone >= 0) {
            return hostAddress.substring(0, zone);
        }
        return hostAddress;
    }
}
