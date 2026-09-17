package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;

/**
 * Group title inside an option list. Modern: accent-colored text followed by a thin rule.
 * Vanilla: centered white text, like the categories on Minecraft's key binds screen.
 */
public class SectionHeader extends Widget {

    private final String text;

    public SectionHeader(String text) {
        this.text = text;
    }

    @Override
    public void draw(UiCanvas c) {
        int ty = c.centeredTextY(y, h);
        if (UiStyle.useVanillaComponents()) {
            c.centeredText(c.trimText(text, w), x + w / 2, ty, UiStyle.VANILLA_TEXT);
            return;
        }
        String visible = c.trimText(text, w - 20);
        c.text(visible, x, ty, Theme.accent);
        int lineX = x + c.textWidth(visible) + 6;
        c.hLine(lineX, ty + c.lineHeight() / 2 - 1, x + w - lineX, Theme.border);
    }
}
