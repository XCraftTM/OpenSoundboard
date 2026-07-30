package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.network.chat.Component;

/** Non-interactive text. Left-aligned by default; call {@link #centered()} to center in bounds. */
public class Label extends Widget {

    private String text;
    private int color = Theme.TEXT;
    private boolean centered = false;

    public Label(String text) {
        this.text = text;
    }

    public Label color(int color) {
        this.color = color;
        return this;
    }

    public Label centered() {
        this.centered = true;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public void draw(UiCanvas c) {
        String visibleText = w > 0 ? c.trimText(text, w) : text;
        if (centered) {
            c.centeredText(Component.literal(visibleText), x + w / 2, y, color);
        } else {
            c.text(visibleText, x, y, color);
        }
    }
}
