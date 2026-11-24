package net.blazemc.login.message;

import java.util.List;
import net.blazemc.login.PluginConstants;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColorParserTest {

    @Test
    void parsesExactBlazePrefixGradient() {
        List<ColorParser.StyledRun> runs = ColorParser.tokenize(PluginConstants.DEFAULT_PREFIX);
        String plain = ColorParser.strip(PluginConstants.DEFAULT_PREFIX);
        assertThat(plain).isEqualTo("BlazeMC Login ∙ ");
        assertThat(runs.stream().noneMatch(run -> run.text().contains("&#") || run.text().contains("&l"))).isTrue();
        assertThat(ColorParser.hexOf(runs.get(0).color())).isEqualTo("#00FB5C");
        assertThat(runs.get(0).text()).isEqualTo("B");
        assertThat(runs.get(0).bold()).isTrue();
        ColorParser.StyledRun lastLetter = runs.stream()
                .filter(r -> r.text().startsWith("n"))
                .reduce((a, b) -> b)
                .orElseThrow();
        assertThat(ColorParser.hexOf(lastLetter.color())).isEqualTo("#FFFFFF");
        assertThat(lastLetter.bold()).isTrue();
        ColorParser.StyledRun bullet = runs.get(runs.size() - 1);
        assertThat(bullet.text()).contains("∙");
        assertThat(bullet.color()).isEqualTo(NamedTextColor.GRAY);
        assertThat(bullet.bold()).isFalse();
    }

    @Test
    void premiumAutoLoginMessageKeepsGradientAndDoesNotExposeCodes() {
        Component component = ColorParser.parse(PluginConstants.DEFAULT_PREMIUM_AUTO_LOGIN);
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        assertThat(plain).isEqualTo("BlazeMC Login ∙ Logat automat cu premium!");
        assertThat(plain).doesNotContain("&#");
        assertThat(plain).doesNotContain("&l");
        assertThat(plain).doesNotContain("&r");
        assertThat(plain).doesNotContain("&7");
    }

    @Test
    void supportsLegacyAndHexAndDecorations() {
        List<ColorParser.StyledRun> runs = ColorParser.tokenize("&aHello &#FF00AA&lWorld&r &7gray");
        assertThat(ColorParser.strip("&aHello &#FF00AA&lWorld&r &7gray")).isEqualTo("Hello World gray");
        assertThat(runs.get(0).color()).isEqualTo(NamedTextColor.GREEN);
        ColorParser.StyledRun world = runs.stream().filter(r -> r.text().equals("World")).findFirst().orElseThrow();
        assertThat(ColorParser.hexOf(world.color())).isEqualTo("#FF00AA");
        assertThat(world.bold()).isTrue();
    }

    @Test
    void resetClearsColorAndDecorations() {
        List<ColorParser.StyledRun> runs = ColorParser.tokenize("&c&lX&rY");
        assertThat(runs.get(0).bold()).isTrue();
        assertThat(runs.get(1).text()).isEqualTo("Y");
        assertThat(runs.get(1).bold()).isFalse();
        assertThat(runs.get(1).color()).isNull();
    }

    @Test
    void prefixIsNotDuplicatedWhenAlreadyPresent() {
        String prefixed = PluginConstants.DEFAULT_PREFIX + "Salut";
        String plain = ColorParser.strip(prefixed);
        assertThat(plain).isEqualTo("BlazeMC Login ∙ Salut");
        assertThat(plain.split("BlazeMC", -1)).hasSize(2);
        assertThat(plain).doesNotContain("&#");
        assertThat(plain).doesNotContain("&l");
    }
}
