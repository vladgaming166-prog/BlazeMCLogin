package net.blazemc.login.message;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.blazemc.login.PluginConstants;
import net.blazemc.login.config.YamlSupport;
import net.blazemc.login.model.LanguageCode;
import net.kyori.adventure.text.Component;

public final class MessageCatalog {

    private final ConcurrentHashMap<String, String> messages = new ConcurrentHashMap<>();
    private volatile String prefixRaw = PluginConstants.DEFAULT_PREFIX;
    private volatile LanguageCode language = LanguageCode.RO;

    public void load(Path dataFolder, LanguageCode language, ClassLoader loader) {
        this.language = language;
        Map<String, Object> bundled = YamlSupport.loadResource(loader, "lang/" + language.resourceFile());
        Map<String, Object> diskLang = YamlSupport.load(dataFolder.resolve("lang").resolve(language.resourceFile()));
        Map<String, Object> overrides = YamlSupport.load(dataFolder.resolve("messages.yml"));
        Map<String, String> merged = new LinkedHashMap<>();
        putAll(merged, bundled);
        putAll(merged, diskLang);
        putAll(merged, overrides);
        messages.clear();
        messages.putAll(merged);
        this.prefixRaw = merged.getOrDefault("prefix", PluginConstants.DEFAULT_PREFIX);
    }

    public LanguageCode language() {
        return language;
    }

    public String prefixRaw() {
        return prefixRaw;
    }

    public Component prefix() {
        return ColorParser.parse(prefixRaw);
    }

    public String raw(String key) {
        return messages.getOrDefault(key, key);
    }

    public String raw(String key, Map<String, String> placeholders) {
        String value = raw(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                value = value.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return value;
    }

    public Component component(String key, Map<String, String> placeholders) {
        String value = raw(key, placeholders);
        if ("prefix".equals(key) || Boolean.parseBoolean(messages.getOrDefault(key + ".includes-prefix", "false"))) {
            return ColorParser.parse(value);
        }
        if (value.startsWith(prefixRaw) || value.contains("&#00FB5C&lB")) {
            return ColorParser.parse(value);
        }
        return prefix().append(ColorParser.parse(value));
    }

    public Component componentRaw(String formatted) {
        return ColorParser.parse(formatted);
    }

    public boolean contains(String key) {
        return messages.containsKey(key);
    }

    private static void putAll(Map<String, String> target, Map<String, Object> source) {
        flatten("", source, target);
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> source, Map<String, String> target) {
        if (source == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                flatten(key, (Map<String, Object>) map, target);
            } else if (value != null) {
                target.put(key, String.valueOf(value));
            }
        }
    }
}
