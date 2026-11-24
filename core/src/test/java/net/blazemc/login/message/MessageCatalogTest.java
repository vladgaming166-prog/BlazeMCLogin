package net.blazemc.login.message;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.blazemc.login.model.LanguageCode;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class MessageCatalogTest {

    @TempDir
    Path temp;

    @Test
    void switchingLanguageTranslatesAllTextIncludingAsciiRomanian() throws Exception {
        writeLang(LanguageCode.RO, "login.success: \"&aTe-ai autentificat cu succes.\"\nprefix: \"PRE \"\n");
        writeLang(LanguageCode.EN, "login.success: \"&aYou have logged in successfully.\"\nprefix: \"PRE \"\n");
        writeLang(LanguageCode.RO_ASCII, "login.success: \"&aTe-ai autentificat cu succes.\"\nprefix: \"PRE \"\n");
        ClassLoader loader = new EmptyLoader();
        MessageCatalog catalog = new MessageCatalog();
        catalog.load(temp, LanguageCode.RO, loader);
        assertThat(plain(catalog, "login.success")).contains("Te-ai autentificat");
        catalog.load(temp, LanguageCode.EN, loader);
        assertThat(plain(catalog, "login.success")).contains("logged in successfully");
        catalog.load(temp, LanguageCode.RO_ASCII, loader);
        assertThat(plain(catalog, "login.success")).doesNotContain("ă").doesNotContain("ș");
        assertThat(plain(catalog, "login.success")).startsWith("PRE ");
    }

    @Test
    void doesNotDuplicatePrefix() throws Exception {
        writeLang(LanguageCode.RO, "prefix: \"PRE \"\nlogin.success: \"PRE ok\"\npremium.auto-login: \"PRE auto\"\npremium.auto-login.includes-prefix: \"true\"\n");
        MessageCatalog catalog = new MessageCatalog();
        catalog.load(temp, LanguageCode.RO, new EmptyLoader());
        String text = plain(catalog, "premium.auto-login");
        assertThat(text).isEqualTo("PRE auto");
        assertThat(text.split("PRE", -1).length).isEqualTo(2);
    }

    private String plain(MessageCatalog catalog, String key) {
        return PlainTextComponentSerializer.plainText().serialize(catalog.component(key, Map.of()));
    }

    private void writeLang(LanguageCode code, String body) throws Exception {
        Path dir = temp.resolve("lang");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(code.resourceFile()), body, StandardCharsets.UTF_8);
    }

    private static final class EmptyLoader extends ClassLoader {
        @Override
        public java.io.InputStream getResourceAsStream(String name) {
            return null;
        }
    }
}
