package net.blazemc.login.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Parses {@code &#RRGGBB} hex colors, {@code &a}/{@code §a} legacy colors, and
 * {@code &l &o &n &m &k &r} decorations into Adventure components.
 */
public final class ColorParser {

    public record StyledRun(String text, TextColor color, boolean bold, boolean italic,
                            boolean underlined, boolean strikethrough, boolean obfuscated) {
    }

    private ColorParser() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        List<StyledRun> runs = tokenize(input);
        TextComponent.Builder root = Component.text();
        for (StyledRun run : runs) {
            if (run.text().isEmpty()) {
                continue;
            }
            Component piece = Component.text(run.text()).style(style -> {
                style.color(run.color());
                apply(style, TextDecoration.BOLD, run.bold());
                apply(style, TextDecoration.ITALIC, run.italic());
                apply(style, TextDecoration.UNDERLINED, run.underlined());
                apply(style, TextDecoration.STRIKETHROUGH, run.strikethrough());
                apply(style, TextDecoration.OBFUSCATED, run.obfuscated());
            });
            root.append(piece);
        }
        return root.build();
    }

    public static List<StyledRun> tokenize(String input) {
        List<StyledRun> runs = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        TextColor color = null;
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;

        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if (next == '#' && i + 7 < input.length() && isHex(input, i + 2, 6)) {
                    flush(runs, buffer, color, bold, italic, underlined, strikethrough, obfuscated);
                    color = TextColor.fromHexString("#" + input.substring(i + 2, i + 8));
                    i += 8;
                    continue;
                }
                char code = Character.toLowerCase(next);
                if (isFormattingCode(code)) {
                    flush(runs, buffer, color, bold, italic, underlined, strikethrough, obfuscated);
                    if (code == 'r') {
                        color = null;
                        bold = italic = underlined = strikethrough = obfuscated = false;
                    } else if (code == 'l') {
                        bold = true;
                    } else if (code == 'o') {
                        italic = true;
                    } else if (code == 'n') {
                        underlined = true;
                    } else if (code == 'm') {
                        strikethrough = true;
                    } else if (code == 'k') {
                        obfuscated = true;
                    } else {
                        color = legacyColor(code);
                    }
                    i += 2;
                    continue;
                }
            }
            buffer.append(c);
            i++;
        }
        flush(runs, buffer, color, bold, italic, underlined, strikethrough, obfuscated);
        return runs;
    }

    public static String strip(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (StyledRun run : tokenize(input)) {
            out.append(run.text());
        }
        return out.toString();
    }

    private static void apply(net.kyori.adventure.text.format.Style.Builder style, TextDecoration decoration, boolean value) {
        if (value) {
            style.decoration(decoration, TextDecoration.State.TRUE);
        } else {
            style.decoration(decoration, TextDecoration.State.NOT_SET);
        }
    }

    private static void flush(
            List<StyledRun> runs,
            StringBuilder buffer,
            TextColor color,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated
    ) {
        if (buffer.length() == 0) {
            return;
        }
        runs.add(new StyledRun(buffer.toString(), color, bold, italic, underlined, strikethrough, obfuscated));
        buffer.setLength(0);
    }

    private static boolean isHex(String input, int start, int length) {
        if (start + length > input.length()) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = input.charAt(start + i);
            if (Character.digit(c, 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFormattingCode(char code) {
        return "0123456789abcdefklmnor".indexOf(Character.toLowerCase(code)) >= 0;
    }

    private static TextColor legacyColor(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> NamedTextColor.BLACK;
            case '1' -> NamedTextColor.DARK_BLUE;
            case '2' -> NamedTextColor.DARK_GREEN;
            case '3' -> NamedTextColor.DARK_AQUA;
            case '4' -> NamedTextColor.DARK_RED;
            case '5' -> NamedTextColor.DARK_PURPLE;
            case '6' -> NamedTextColor.GOLD;
            case '7' -> NamedTextColor.GRAY;
            case '8' -> NamedTextColor.DARK_GRAY;
            case '9' -> NamedTextColor.BLUE;
            case 'a' -> NamedTextColor.GREEN;
            case 'b' -> NamedTextColor.AQUA;
            case 'c' -> NamedTextColor.RED;
            case 'd' -> NamedTextColor.LIGHT_PURPLE;
            case 'e' -> NamedTextColor.YELLOW;
            case 'f' -> NamedTextColor.WHITE;
            default -> null;
        };
    }

    public static String hexOf(TextColor color) {
        if (color == null) {
            return null;
        }
        return String.format(Locale.ROOT, "#%06X", color.value());
    }
}
