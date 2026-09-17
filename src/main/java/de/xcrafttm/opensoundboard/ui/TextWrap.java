package de.xcrafttm.opensoundboard.ui;

import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/** Word wrapping that respects the user's font scale; shared by tooltips and option rows. */
public final class TextWrap {

    private TextWrap() {
    }

    public static int width(Font font, String text) {
        return (int) Math.ceil(font.width(text) * UiStyle.fontScale());
    }

    /**
     * Wrap {@code text} into lines no wider than {@code maxWidth}. When {@code maxLines} is reached,
     * the last line is cut with an ellipsis.
     */
    public static List<String> wrap(Font font, String text, int maxWidth, int maxLines) {
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && width(font, candidate) > maxWidth) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            out.add(line.toString());
        }
        if (maxLines > 0 && out.size() > maxLines) {
            List<String> cut = new ArrayList<>(out.subList(0, maxLines));
            String last = cut.get(maxLines - 1) + " " + out.get(maxLines);
            cut.set(maxLines - 1, trim(font, last + "...", maxWidth));
            return cut;
        }
        for (int i = 0; i < out.size(); i++) {
            if (width(font, out.get(i)) > maxWidth) out.set(i, trim(font, out.get(i), maxWidth));
        }
        return out;
    }

    private static String trim(Font font, String s, int maxWidth) {
        if (width(font, s) <= maxWidth) return s;
        int raw = (int) Math.floor(maxWidth / UiStyle.fontScale());
        return font.plainSubstrByWidth(s, Math.max(0, raw - font.width("..."))) + "...";
    }
}
