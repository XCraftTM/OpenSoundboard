package de.xcrafttm.opensoundboard.tools;

import com.mojang.blaze3d.platform.InputConstants;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.File;

/**
 * Small text/time helpers shared by the screens. Mojang-mapped and version-neutral
 * ({@code Font}, {@code InputConstants}, {@code Component} are stable across the whole span).
 */
public final class GuiTools {

    private GuiTools() {
    }

    public static String baseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Trims a string to fit maxWidth pixels, appending "..." if truncated. */
    public static String trimName(Font f, String name, int maxWidth) {
        if (f.width(name) <= maxWidth) return name;
        return f.plainSubstrByWidth(name, Math.max(0, maxWidth - f.width("..."))) + "...";
    }

    public static String formatTimeSeconds(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public static String formatTimeMillis(long millis) {
        long totalSeconds = millis / 1000;
        int mins = (int) (totalSeconds / 60);
        int secs = (int) (totalSeconds % 60);
        int tenths = (int) ((millis % 1000) / 100);
        return String.format("%d:%02d.%d", mins, secs, tenths);
    }

    public static int parseTimeSeconds(String input) {
        long ms = parseTimeMillis(input);
        return ms < 0 ? -1 : (int) (ms / 1000);
    }

    /** Parses "m:ss", "m:ss.t", or plain seconds into milliseconds, or -1 if invalid. */
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

    public static Component keyBindLabel(@Nullable SoundboardConfig.KeyBind keyBind) {
        if (keyBind == null) {
            return Component.translatable("gui.opensoundboard.keybind.prefix")
                    .append(Component.translatable("gui.opensoundboard.keybind.none"));
        }
        Component keyName = InputConstants.Type.KEYSYM.getOrCreate(keyBind.getKeyCode()).getDisplayName();
        MutableComponent full = Component.empty();
        if ((keyBind.getModifiers() & GLFW.GLFW_MOD_CONTROL) != 0) full.append("Ctrl + ");
        if ((keyBind.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0) full.append("Shift + ");
        if ((keyBind.getModifiers() & GLFW.GLFW_MOD_ALT) != 0) full.append("Alt + ");
        full.append(keyName);
        return Component.translatable("gui.opensoundboard.keybind.prefix").append(full);
    }
}
