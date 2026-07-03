package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Flat button. Primary = filled indigo; secondary = subtle row fill with a border.
 */
public class Button extends Widget {

    private Component label;
    private final Consumer<Button> onClick;
    private boolean primary = true;

    public Button(Component label, Consumer<Button> onClick) {
        this.label = label;
        this.onClick = onClick;
    }

    public Button secondary() {
        this.primary = false;
        return this;
    }

    public void setLabel(Component label) {
        this.label = label;
    }

    @Override
    public void draw(UiCanvas c) {
        boolean hover = active && c.hovered(x, y, w, h);
        if (primary) {
            c.fillRoundRect(x, y, w, h, active ? (hover ? Theme.ACCENT_HOVER : Theme.ACCENT) : Theme.ROW);
        } else {
            c.fillRoundRect(x, y, w, h, hover ? Theme.ROW_HOVER : Theme.ROW);
            c.roundBorder(x, y, w, h, hover ? Theme.BORDER_STRONG : Theme.BORDER);
        }
        int textColor = !active ? Theme.TEXT_MUTED : (primary ? Theme.TEXT_ON_ACCENT : Theme.TEXT);
        c.centeredText(label, x + w / 2, y + (h - 8) / 2, textColor);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && active) {
            onClick.accept(this);
            return true;
        }
        return false;
    }
}
