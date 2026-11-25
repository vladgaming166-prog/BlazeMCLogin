package net.blazemc.login.paper.premium;

import net.blazemc.login.premium.TrustedAuthStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrustedAuthProvidersTest {

    @Test
    void interpretsPremiumOrCrackedStatuses() {
        assertEquals(TrustedAuthStatus.PREMIUM, TrustedAuthProviders.interpret("PREMIUM", "getStatus"));
        assertEquals(TrustedAuthStatus.PREMIUM, TrustedAuthProviders.interpret("Premium", "getAccountType"));
        assertEquals(TrustedAuthStatus.CRACKED, TrustedAuthProviders.interpret("CRACKED", "getStatus"));
        assertEquals(TrustedAuthStatus.PENDING, TrustedAuthProviders.interpret("UNKNOWN", "getStatus"));
        assertEquals(TrustedAuthStatus.PENDING, TrustedAuthProviders.interpret(null, "getStatus"));
        assertEquals(TrustedAuthStatus.PREMIUM, TrustedAuthProviders.interpret(Boolean.TRUE, "isPremium"));
        assertEquals(TrustedAuthStatus.PENDING, TrustedAuthProviders.interpret(Boolean.FALSE, "isPremium"));
        assertEquals(TrustedAuthStatus.CRACKED, TrustedAuthProviders.interpret(Boolean.TRUE, "isCracked"));
    }
}
