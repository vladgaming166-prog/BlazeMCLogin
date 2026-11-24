package net.blazemc.login.security;

public interface PasswordHasher {

    String hash(char[] password);

    boolean verify(char[] password, String storedHash);

    boolean needsRehash(String storedHash);

    String algorithmName();
}
