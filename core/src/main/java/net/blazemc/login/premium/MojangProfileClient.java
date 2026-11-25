package net.blazemc.login.premium;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks up whether a username currently belongs to a Mojang/Microsoft profile.
 * A 200 means the name is Premium; 204/404 means it is not. Any transport or
 * HTTP error is {@link ProfileStatus#UNAVAILABLE} so callers can fail closed.
 */
public final class MojangProfileClient implements MinecraftProfileLookup {

    private static final String ENDPOINT = "https://api.mojang.com/users/profiles/minecraft/";
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final long cacheTtlMillis;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public MojangProfileClient() {
        this(3_000, 3_000, 60_000);
    }

    public MojangProfileClient(int connectTimeoutMillis, int readTimeoutMillis, long cacheTtlMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.cacheTtlMillis = cacheTtlMillis;
    }

    @Override
    public ProfileStatus lookup(String username) {
        if (username == null || username.isBlank() || username.length() > 16 || !username.matches("[A-Za-z0-9_]+")) {
            return ProfileStatus.CRACKED;
        }
        String key = username.toLowerCase(Locale.ROOT);
        Cached cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.at < cacheTtlMillis && cached.status != ProfileStatus.UNAVAILABLE) {
            return cached.status;
        }
        ProfileStatus live = fetch(username);
        if (live != ProfileStatus.UNAVAILABLE) {
            cache.put(key, new Cached(live, now));
            return live;
        }
        if (cached != null && cached.status == ProfileStatus.PREMIUM) {
            return ProfileStatus.PREMIUM;
        }
        return ProfileStatus.UNAVAILABLE;
    }

    ProfileStatus fetch(String username) {
        HttpURLConnection connection = null;
        try {
            String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
            URI uri = URI.create(ENDPOINT + encoded);
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "BlazeMCLogin/1.1.1 (BlazeMC Network)");
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code == 200) {
                return ProfileStatus.PREMIUM;
            }
            if (code == 204 || code == 404) {
                return ProfileStatus.CRACKED;
            }
            if (code == 429) {
                return ProfileStatus.UNAVAILABLE;
            }
            return ProfileStatus.UNAVAILABLE;
        } catch (IOException | RuntimeException ex) {
            return ProfileStatus.UNAVAILABLE;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void putCache(String username, ProfileStatus status) {
        cache.put(username.toLowerCase(Locale.ROOT), new Cached(status, System.currentTimeMillis()));
    }

    public Map<String, Cached> cacheView() {
        return Map.copyOf(cache);
    }

    public record Cached(ProfileStatus status, long at) {
    }
}
