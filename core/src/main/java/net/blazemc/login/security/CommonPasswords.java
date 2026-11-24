package net.blazemc.login.security;

import java.util.Locale;
import java.util.Set;

public final class CommonPasswords {

    private static final Set<String> COMMON = Set.of(
            "123456", "123456789", "12345678", "password", "qwerty", "1234567", "111111",
            "1234567890", "123123", "abc123", "password1", "1234", "iloveyou", "1q2w3e4r",
            "000000", "qwerty123", "zaq12wsx", "dragon", "sunshine", "princess", "letmein",
            "welcome", "monkey", "login", "admin", "qwertyuiop", "passw0rd", "starwars",
            "password123", "minecraft", "minecraft123", "blazemc", "blazemc123", "survival",
            "server", "cracked", "premium", "parola", "parola1", "parola123", "romania",
            "qwerty1", "football", "baseball", "shadow", "master", "hello", "charlie",
            "aa123456", "donald", "password12", "qazwsx", "trustno1", "654321", "jordan23",
            "superman", "harley", "robert", "matthew", "jordan", "hunter", "ranger",
            "buster", "thomas", "tigger", "robert1", "soccer", "batman", "test", "test123",
            "user", "root", "toor", "pass", "pass123", "changeme", "default", "guest",
            "azerty", "azerty123", "lol123", "killer", "hockey", "computer", "michelle",
            "andrea", "andrew", "access", "secret", "secret123", "blaze", "blaze123",
            "herobrine", "notch", "steve", "alex123", "pvp123", "anarchy", "kitpvp",
            "123qwe", "qwe123", "asd123", "1qaz2wsx", "zxcvbnm", "asdfgh", "asdfghjkl",
            "parolamea", "parolamea1", "nume123", "joc123", "server123"
    );

    private CommonPasswords() {
    }

    public static boolean isCommon(String password) {
        if (password == null) {
            return true;
        }
        return COMMON.contains(password.toLowerCase(Locale.ROOT).trim());
    }
}
