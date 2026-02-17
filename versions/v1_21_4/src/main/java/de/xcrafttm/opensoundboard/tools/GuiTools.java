package de.xcrafttm.opensoundboard.tools;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.File;

/**
 * Small GUI helpers shared between screens. Keeps UI code cleaner.
 */
public final class GuiTools {

    private GuiTools() {
    }

    public static String baseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static String trimName(TextRenderer tr, String name, int maxWidth) {
        if (tr.getWidth(name) <= maxWidth) return name;
        return tr.trimToWidth(name, Math.max(0, maxWidth - tr.getWidth("..."))) + "...";
    }

    public static String formatTimeSeconds(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", mins, secs);
    }

    public static String formatTimeMillis(long millis) {
        long totalSeconds = millis / 1000;
        int mins = (int) (totalSeconds / 60);
        int secs = (int) (totalSeconds % 60);
        int tenths = (int) ((millis % 1000) / 100);
        return String.format("%d:%02d.%d", mins, secs, tenths);
    }

    /**
     * @return seconds parsed from either "m:ss", "m:ss.t" or plain seconds, or -1 if invalid
     */
    public static int parseTimeSeconds(String input) {
        long ms = parseTimeMillis(input);
        return ms < 0 ? -1 : (int) (ms / 1000);
    }

    /**
     * @return millis parsed from "m:ss", "m:ss.t", or plain seconds, or -1 if invalid
     */
    public static long parseTimeMillis(String input) {
        try {
            input = input.trim();
            long mins = 0;
            String secPart = input;

            if (input.contains(":")) {
                var parts = input.split(":", 2);
                mins = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
                secPart = parts[1];
            }

            long secs = 0;
            long millis = 0;
            if (secPart.contains(".")) {
                var dotParts = secPart.split("\\.", 2);
                secs = dotParts[0].isEmpty() ? 0 : Long.parseLong(dotParts[0]);
                String frac = dotParts[1];
                if (!frac.isEmpty()) {
                    if (frac.length() == 1) frac = frac + "00";
                    else if (frac.length() == 2) frac = frac + "0";
                    else frac = frac.substring(0, 3);
                    millis = Long.parseLong(frac);
                }
            } else {
                secs = secPart.isEmpty() ? 0 : Long.parseLong(secPart);
            }

            return mins * 60000 + secs * 1000 + millis;
        } catch (Exception ignored) {
            return -1;
        }
    }

    public static Text favoriteLabel(boolean fav) {
        return Text.literal(fav ? "\u2605" : "\u2606").formatted(fav ? Formatting.GOLD : Formatting.GRAY);
    }

    public static Text loopLabel(boolean loopAll) {
        return Text.literal(loopAll ? "\uD83D\uDD01" : "\uD83D\uDD04")
                .formatted(loopAll ? Formatting.WHITE : Formatting.GRAY);
    }

    public static Text keyBindLabel(@Nullable SoundboardConfig.KeyBind keyBind) {
        if (keyBind == null) {
            return Text.translatable("gui.opensoundboard.keybind.prefix")
                    .append(Text.translatable("gui.opensoundboard.keybind.none"));
        }

        Text keyName = InputUtil.fromKeyCode(keyBind.getKeyCode(), keyBind.getScanCode())
                .getLocalizedText();

        MutableText fullText = Text.empty();
        if ((keyBind.getModifiers() & GLFW.GLFW_MOD_CONTROL) != 0) fullText.append("Ctrl + ");
        if ((keyBind.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0) fullText.append("Shift + ");
        if ((keyBind.getModifiers() & GLFW.GLFW_MOD_ALT) != 0) fullText.append("Alt + ");
        fullText.append(keyName);

        return Text.translatable("gui.opensoundboard.keybind.prefix").append(fullText);
    }
}
