package net.blazemc.login.security;

import com.password4j.Argon2Function;
import com.password4j.Hash;
import com.password4j.Password;
import com.password4j.types.Argon2;
import java.util.Locale;
import java.util.Objects;

public final class Argon2PasswordHasher implements PasswordHasher {

    private final int memoryKib;
    private final int iterations;
    private final int parallelism;
    private final Argon2Function function;

    public Argon2PasswordHasher(int memoryKib, int iterations, int parallelism) {
        if (memoryKib < 8192) {
            throw new IllegalArgumentException("Argon2 memory_kib must be at least 8192");
        }
        if (iterations < 1) {
            throw new IllegalArgumentException("Argon2 iterations must be at least 1");
        }
        if (parallelism < 1) {
            throw new IllegalArgumentException("Argon2 parallelism must be at least 1");
        }
        this.memoryKib = memoryKib;
        this.iterations = iterations;
        this.parallelism = parallelism;
        this.function = Argon2Function.getInstance(memoryKib, iterations, parallelism, 32, Argon2.ID);
    }

    @Override
    public String hash(char[] password) {
        Objects.requireNonNull(password, "password");
        if (password.length == 0) {
            throw new IllegalArgumentException("password must not be empty");
        }
        Hash hash = Password.hash(new String(password)).addRandomSalt(16).with(function);
        return hash.getResult();
    }

    @Override
    public boolean verify(char[] password, String storedHash) {
        if (password == null || storedHash == null || storedHash.isBlank() || password.length == 0) {
            return false;
        }
        try {
            return Password.check(new String(password), storedHash).with(functionFor(storedHash));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public boolean needsRehash(String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return true;
        }
        String lower = storedHash.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("$argon2id$")) {
            return true;
        }
        return !lower.contains("m=" + memoryKib) || !lower.contains("t=" + iterations) || !lower.contains("p=" + parallelism);
    }

    @Override
    public String algorithmName() {
        return "ARGON2ID";
    }

    private Argon2Function functionFor(String storedHash) {
        try {
            return Argon2Function.getInstanceFromHash(storedHash);
        } catch (RuntimeException ex) {
            return function;
        }
    }
}
