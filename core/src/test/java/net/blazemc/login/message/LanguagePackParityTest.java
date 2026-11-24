package net.blazemc.login.message;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.blazemc.login.config.YamlSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguagePackParityTest {

    @Test
    void romanianEnglishAndAsciiPacksShareTheSameKeys() throws Exception {
        Path lang = Path.of("").toAbsolutePath();
        Path resources = findLangDir(lang);
        assertThat(resources).isNotNull();
        Set<String> ro = keys(resources.resolve("messages-ro.yml"));
        Set<String> en = keys(resources.resolve("messages-en.yml"));
        Set<String> ascii = keys(resources.resolve("messages-ro-ascii.yml"));
        assertThat(ro).isNotEmpty();
        assertThat(en).containsExactlyInAnyOrderElementsOf(ro);
        assertThat(ascii).containsExactlyInAnyOrderElementsOf(ro);
        assertThat(ro).contains("prefix", "login.success", "register.success", "premium.auto-login", "admin.language");
        String asciiBody = Files.readString(resources.resolve("messages-ro-ascii.yml"));
        assertThat(asciiBody).doesNotContain("ă", "â", "î", "ș", "ț", "Ă", "Â", "Î", "Ș", "Ț");
    }

    private static Set<String> keys(Path file) {
        Map<String, Object> map = YamlSupport.load(file);
        java.util.LinkedHashMap<String, String> flat = new java.util.LinkedHashMap<>();
        flatten("", map, flat);
        return new TreeSet<>(flat.keySet());
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> source, Map<String, String> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                flatten(key, (Map<String, Object>) nested, target);
            } else if (entry.getValue() != null) {
                target.put(key, String.valueOf(entry.getValue()));
            }
        }
    }

    private static Path findLangDir(Path start) {
        Path current = start;
        for (int i = 0; i < 6; i++) {
            Path candidate = current.resolve("paper/src/main/resources/lang");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            Path named = current.resolve("BlazeMCLogin/paper/src/main/resources/lang");
            if (Files.isDirectory(named)) {
                return named;
            }
            current = current.getParent();
            if (current == null) {
                break;
            }
        }
        Path workspace = Path.of("/workspace/paper/src/main/resources/lang");
        return Files.isDirectory(workspace) ? workspace : null;
    }
}
