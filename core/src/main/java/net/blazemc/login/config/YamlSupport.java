package net.blazemc.login.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.Yaml;

public final class YamlSupport {

    private YamlSupport() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> load(Path path) {
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            if (loaded instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return new LinkedHashMap<>();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read " + path, ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadResource(ClassLoader loader, String name) {
        try (InputStream in = loader.getResourceAsStream(name)) {
            if (in == null) {
                return new LinkedHashMap<>();
            }
            Object loaded = new Yaml().load(in);
            if (loaded instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return new LinkedHashMap<>();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read resource " + name, ex);
        }
    }

    public static Optional<Object> find(Map<String, Object> root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return Optional.empty();
        }
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return Optional.empty();
            }
            current = map.get(part);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    @SafeVarargs
    public static boolean boolOverlay(boolean fallback, String path, Map<String, Object>... maps) {
        for (Map<String, Object> map : maps) {
            Optional<Object> found = find(map, path);
            if (found.isPresent()) {
                return bool(Map.of("_", found.get()), "_", fallback);
            }
        }
        return fallback;
    }

    @SafeVarargs
    public static String stringOverlay(String fallback, String path, Map<String, Object>... maps) {
        for (Map<String, Object> map : maps) {
            Optional<Object> found = find(map, path);
            if (found.isPresent() && found.get() != null) {
                return String.valueOf(found.get());
            }
        }
        return fallback;
    }

    @SafeVarargs
    public static int intOverlay(int fallback, String path, Map<String, Object>... maps) {
        for (Map<String, Object> map : maps) {
            Optional<Object> found = find(map, path);
            if (found.isPresent()) {
                Object value = found.get();
                if (value instanceof Number number) {
                    return number.intValue();
                }
                if (value instanceof String text) {
                    try {
                        return Integer.parseInt(text.trim());
                    } catch (NumberFormatException ignored) {
                        return fallback;
                    }
                }
            }
        }
        return fallback;
    }

    @SafeVarargs
    public static double decimalOverlay(double fallback, String path, Map<String, Object>... maps) {
        for (Map<String, Object> map : maps) {
            Optional<Object> found = find(map, path);
            if (found.isPresent()) {
                Object value = found.get();
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
                if (value instanceof String text) {
                    try {
                        return Double.parseDouble(text.trim());
                    } catch (NumberFormatException ignored) {
                        return fallback;
                    }
                }
            }
        }
        return fallback;
    }

    public static Object value(Map<String, Object> root, String path, Object fallback) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return fallback;
            }
            current = map.get(part);
            if (current == null) {
                return fallback;
            }
        }
        return current;
    }

    public static String string(Map<String, Object> root, String path, String fallback) {
        Object value = value(root, path, fallback);
        return value == null ? fallback : String.valueOf(value);
    }

    public static int integer(Map<String, Object> root, String path, int fallback) {
        Object value = value(root, path, fallback);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static double decimal(Map<String, Object> root, String path, double fallback) {
        Object value = value(root, path, fallback);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean bool(Map<String, Object> root, String path, boolean fallback) {
        Object value = value(root, path, fallback);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public static List<String> stringList(Map<String, Object> root, String path, List<String> fallback) {
        Object value = value(root, path, fallback);
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
            return List.copyOf(out);
        }
        return fallback;
    }
}
